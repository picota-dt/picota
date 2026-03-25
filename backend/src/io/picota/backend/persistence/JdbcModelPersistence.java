package io.picota.backend.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.picota.backend.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

public class JdbcModelPersistence implements ModelPersistence {
	private static final com.fasterxml.jackson.core.type.TypeReference<List<DigitalSubject>> SUBJECTS_TYPE =
			new com.fasterxml.jackson.core.type.TypeReference<>() {
			};
	private static final com.fasterxml.jackson.core.type.TypeReference<Map<String, VariableStat>> DATASET_STATS_TYPE =
			new com.fasterxml.jackson.core.type.TypeReference<>() {
			};

	private final PersistenceConfig config;
	private final ObjectMapper mapper;

	public JdbcModelPersistence(PersistenceConfig config) {
		this.config = config == null ? new PersistenceConfig(PersistenceEngine.SQLITE, Path.of("picota.db"), null, 3306, null, null, null, null) : config;
		this.mapper = new ObjectMapper().findAndRegisterModules();
		loadDriver();
		ensureStorageReady();
		initializeSchema();
	}

	@Override
	public Optional<Application> loadModel() {
		try (Connection connection = openConnection()) {
			List<UserAccount> users = loadUsers(connection);
			List<TwinAggregate> twins = loadTwins(connection);
			return Optional.of(new Application(users, List.of(), twins, List.of()));
		} catch (SQLException e) {
			throw new PersistenceException("Unable to load application model from database", e);
		}
	}

	@Override
	public void saveModel(Application model) {
		Application safeModel = model == null ? Application.empty() : model;
		try (Connection connection = openConnection()) {
			connection.setAutoCommit(false);
			try {
				clearTables(connection);
				insertUsers(connection, safeModel.users());
				insertTwins(connection, safeModel.twins());
				insertDatasets(connection, safeModel.twins());
				connection.commit();
			} catch (SQLException | RuntimeException e) {
				connection.rollback();
				throw e;
			} finally {
				connection.setAutoCommit(true);
			}
		} catch (SQLException e) {
			throw new PersistenceException("Unable to save application model", e);
		}
	}

	@Override
	public void close() {
		// connections are opened per operation; nothing to close here.
	}

	private void loadDriver() {
		String driverClass = config.engine() == PersistenceEngine.SQLITE
				? "org.sqlite.JDBC"
				: "com.mysql.cj.jdbc.Driver";
		try {
			Class.forName(driverClass);
		} catch (ClassNotFoundException e) {
			throw new PersistenceException("JDBC driver not available: " + driverClass, e);
		}
	}

	private void ensureStorageReady() {
		if (config.engine() != PersistenceEngine.SQLITE || config.hasJdbcUrlOverride()) return;
		try {
			Path parent = config.sqliteFile().getParent();
			if (parent != null) Files.createDirectories(parent);
		} catch (IOException e) {
			throw new PersistenceException("Unable to create SQLite storage directory", e);
		}
	}

	private void initializeSchema() {
		List<String> statements = config.engine() == PersistenceEngine.SQLITE ? sqliteSchemaStatements() : mysqlSchemaStatements();
		try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
			for (String sql : statements) {
				statement.execute(sql);
			}
			ensureIngestionTokenColumn(connection);
		} catch (SQLException e) {
			throw new PersistenceException("Unable to initialize persistence schema", e);
		}
	}

	private Connection openConnection() throws SQLException {
		Connection connection;
		if (config.engine() == PersistenceEngine.SQLITE) {
			connection = DriverManager.getConnection(config.jdbcUrl());
			enableSqliteForeignKeys(connection);
			return connection;
		}
		if (config.mysqlUser().isBlank() && config.mysqlPassword().isBlank()) {
			connection = DriverManager.getConnection(config.jdbcUrl());
			return connection;
		}
		Properties props = new Properties();
		if (!config.mysqlUser().isBlank()) props.setProperty("user", config.mysqlUser());
		if (!config.mysqlPassword().isBlank()) props.setProperty("password", config.mysqlPassword());
		connection = DriverManager.getConnection(config.jdbcUrl(), props);
		return connection;
	}

	private List<UserAccount> loadUsers(Connection connection) throws SQLException {
		String sql = "select id, name, email, password_hash, avatar_initials, credits, joined_at from users";
		try (PreparedStatement statement = connection.prepareStatement(sql);
			 ResultSet rs = statement.executeQuery()) {
			List<UserAccount> users = new ArrayList<>();
			while (rs.next()) {
				users.add(new UserAccount(
						rs.getString("id"),
						rs.getString("name"),
						rs.getString("email"),
						rs.getString("password_hash"),
						rs.getString("avatar_initials"),
						rs.getInt("credits"),
						parseInstant(rs.getString("joined_at"))
				));
			}
			users.sort(Comparator.comparing(UserAccount::id));
			return users;
		}
	}

	private List<TwinAggregate> loadTwins(Connection connection) throws SQLException {
		Map<String, LoadedTwinRow> loaded = new LinkedHashMap<>();
		String twinsSql = "select id, owner_user_id, name, description, version, image, type, status, updated_at, credits_used, model_content, subjects_json, inference_engine_json, ingestion_token " +
				"from twins order by id";
		try (PreparedStatement statement = connection.prepareStatement(twinsSql);
			 ResultSet rs = statement.executeQuery()) {
			while (rs.next()) {
				String twinId = rs.getString("id");
				List<DigitalSubject> subjects = readJson(rs.getString("subjects_json"), SUBJECTS_TYPE, List::of);
				InferenceEngine inferenceEngine = readJson(rs.getString("inference_engine_json"), InferenceEngine.class, () -> null);
				DigitalTwin twin = new DigitalTwin(
						twinId,
						rs.getString("name"),
						rs.getString("description"),
						rs.getString("version"),
						rs.getString("image"),
						parseTwinType(rs.getString("type")),
						parseTwinStatus(rs.getString("status")),
						rs.getString("updated_at"),
						rs.getInt("credits_used"),
						rs.getString("model_content"),
						subjects,
						inferenceEngine,
						List.of(),
						rs.getString("ingestion_token")
				);
				loaded.put(twinId, new LoadedTwinRow(rs.getString("owner_user_id"), twin));
			}
		}

		String datasetsSql = "select twin_id, subject_id, file_name, uploaded_records, realtime_records, uploaded_at, stats_json " +
				"from datasets order by twin_id, subject_id";
		try (PreparedStatement statement = connection.prepareStatement(datasetsSql);
			 ResultSet rs = statement.executeQuery()) {
			while (rs.next()) {
				String twinId = rs.getString("twin_id");
				LoadedTwinRow row = loaded.get(twinId);
				if (row == null) continue;
				Map<String, VariableStat> stats = readJson(rs.getString("stats_json"), DATASET_STATS_TYPE, Map::of);
				SubjectDataset dataset = new SubjectDataset(
						rs.getString("subject_id"),
						rs.getString("file_name"),
						nullableInt(rs, "uploaded_records"),
						nullableInt(rs, "realtime_records"),
						parseInstant(rs.getString("uploaded_at")),
						stats
				);
				row.datasets().add(dataset);
			}
		}

		return loaded.values().stream()
				.map(row -> new TwinAggregate(row.ownerUserId(), withDatasets(row.twin(), row.datasets())))
				.sorted(Comparator.comparing(aggregate -> aggregate.twin().id()))
				.toList();
	}

	private void clearTables(Connection connection) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.executeUpdate("delete from datasets");
			statement.executeUpdate("delete from twins");
			statement.executeUpdate("delete from users");
		}
	}

	private void insertUsers(Connection connection, List<UserAccount> users) throws SQLException {
		String sql = "insert into users (id, name, email, password_hash, avatar_initials, credits, joined_at) values (?, ?, ?, ?, ?, ?, ?)";
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			for (UserAccount user : users) {
				if (user == null || user.id() == null || user.id().isBlank()) continue;
				statement.setString(1, user.id());
				statement.setString(2, nullToEmpty(user.name()));
				statement.setString(3, nullToEmpty(user.email()));
				statement.setString(4, nullToEmpty(user.passwordHash()));
				statement.setString(5, user.avatarInitials());
				statement.setInt(6, user.credits());
				if (user.joinedAt() == null) statement.setNull(7, Types.VARCHAR);
				else statement.setString(7, user.joinedAt().toString());
				statement.addBatch();
			}
			statement.executeBatch();
		}
	}

	private void insertTwins(Connection connection, List<TwinAggregate> twins) throws SQLException {
		String sql = "insert into twins (id, owner_user_id, name, description, version, image, type, status, updated_at, credits_used, model_content, subjects_json, inference_engine_json, ingestion_token) " +
				"values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			for (TwinAggregate aggregate : twins) {
				if (aggregate == null || aggregate.ownerUserId() == null || aggregate.ownerUserId().isBlank() || aggregate.twin() == null) {
					continue;
				}
				DigitalTwin twin = aggregate.twin();
				if (twin.id() == null || twin.id().isBlank()) continue;
				statement.setString(1, twin.id());
				statement.setString(2, aggregate.ownerUserId());
				statement.setString(3, nullToEmpty(twin.name()));
				statement.setString(4, twin.description());
				statement.setString(5, nullToEmpty(twin.version()));
				statement.setString(6, twin.image());
				statement.setString(7, twin.type() == null ? TwinType.OTHER.name() : twin.type().name());
				statement.setString(8, twin.status() == null ? TwinStatus.DRAFT.name() : twin.status().name());
				statement.setString(9, twin.updatedAt());
				statement.setInt(10, twin.creditsUsed() == null ? 0 : twin.creditsUsed());
				statement.setString(11, twin.model());
				statement.setString(12, writeJson(twin.subjects()));
				if (twin.inferenceEngine() == null) statement.setNull(13, Types.VARCHAR);
				else statement.setString(13, writeJson(twin.inferenceEngine()));
				statement.setString(14, twin.ingestionToken());
				statement.addBatch();
			}
			statement.executeBatch();
		}
	}

	private void insertDatasets(Connection connection, List<TwinAggregate> twins) throws SQLException {
		String sql = "insert into datasets (twin_id, subject_id, file_name, uploaded_records, realtime_records, uploaded_at, stats_json) " +
				"values (?, ?, ?, ?, ?, ?, ?)";
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			for (TwinAggregate aggregate : twins) {
				if (aggregate == null || aggregate.twin() == null || aggregate.twin().id() == null) continue;
				DigitalTwin twin = aggregate.twin();
				Map<String, SubjectDataset> bySubject = new LinkedHashMap<>();
				for (SubjectDataset dataset : twin.datasets()) {
					if (dataset == null || dataset.subjectId() == null || dataset.subjectId().isBlank()) continue;
					bySubject.put(dataset.subjectId(), dataset);
				}
				for (SubjectDataset dataset : bySubject.values()) {
					statement.setString(1, twin.id());
					statement.setString(2, dataset.subjectId());
					statement.setString(3, dataset.fileName());
					if (dataset.uploadedRecords() == null) statement.setNull(4, Types.INTEGER);
					else statement.setInt(4, dataset.uploadedRecords());
					if (dataset.realtimeRecords() == null) statement.setNull(5, Types.INTEGER);
					else statement.setInt(5, dataset.realtimeRecords());
					if (dataset.uploadedAt() == null) statement.setNull(6, Types.VARCHAR);
					else statement.setString(6, dataset.uploadedAt().toString());
					statement.setString(7, writeJson(dataset.stats()));
					statement.addBatch();
				}
			}
			statement.executeBatch();
		}
	}

	private List<String> sqliteSchemaStatements() {
		return List.of(
				"pragma foreign_keys = on",
				"drop table if exists app_model_state",
				"create table if not exists users (" +
						"id text primary key, " +
						"name text not null, " +
						"email text not null unique, " +
						"password_hash text not null, " +
						"avatar_initials text, " +
						"credits integer not null default 0, " +
						"joined_at text" +
						")",
				"create table if not exists twins (" +
						"id text primary key, " +
						"owner_user_id text not null, " +
						"name text not null, " +
						"description text, " +
						"version text not null, " +
						"image text, " +
						"type text not null, " +
						"status text not null, " +
						"updated_at text, " +
						"credits_used integer not null default 0, " +
						"model_content text, " +
						"subjects_json text not null, " +
						"inference_engine_json text, " +
						"ingestion_token text, " +
						"foreign key(owner_user_id) references users(id) on delete cascade" +
						")",
				"create index if not exists idx_twins_owner_user on twins(owner_user_id)",
				"create table if not exists datasets (" +
						"twin_id text not null, " +
						"subject_id text not null, " +
						"file_name text, " +
						"uploaded_records integer, " +
						"realtime_records integer, " +
						"uploaded_at text, " +
						"stats_json text not null, " +
						"primary key (twin_id, subject_id), " +
						"foreign key(twin_id) references twins(id) on delete cascade" +
						")",
				"create index if not exists idx_datasets_twin on datasets(twin_id)"
		);
	}

	private List<String> mysqlSchemaStatements() {
		return List.of(
				"drop table if exists app_model_state",
				"create table if not exists users (" +
						"id varchar(64) primary key, " +
						"name varchar(255) not null, " +
						"email varchar(320) not null unique, " +
						"password_hash varchar(255) not null, " +
						"avatar_initials varchar(16), " +
						"credits int not null default 0, " +
						"joined_at varchar(64)" +
						") engine=InnoDB default charset=utf8mb4",
				"create table if not exists twins (" +
						"id varchar(64) primary key, " +
						"owner_user_id varchar(64) not null, " +
						"name varchar(255) not null, " +
						"description text, " +
						"version varchar(32) not null, " +
						"image text, " +
						"type varchar(64) not null, " +
						"status varchar(64) not null, " +
						"updated_at varchar(64), " +
						"credits_used int not null default 0, " +
						"model_content longtext, " +
						"subjects_json longtext not null, " +
						"inference_engine_json longtext, " +
						"ingestion_token varchar(255), " +
						"constraint fk_twins_owner foreign key (owner_user_id) references users(id) on delete cascade" +
						") engine=InnoDB default charset=utf8mb4",
				"create index if not exists idx_twins_owner_user on twins(owner_user_id)",
				"create table if not exists datasets (" +
						"twin_id varchar(64) not null, " +
						"subject_id varchar(128) not null, " +
						"file_name varchar(255), " +
						"uploaded_records int, " +
						"realtime_records int, " +
						"uploaded_at varchar(64), " +
						"stats_json longtext not null, " +
						"primary key (twin_id, subject_id), " +
						"constraint fk_datasets_twin foreign key (twin_id) references twins(id) on delete cascade" +
						") engine=InnoDB default charset=utf8mb4",
				"create index if not exists idx_datasets_twin on datasets(twin_id)"
		);
	}

	private void ensureIngestionTokenColumn(Connection connection) throws SQLException {
		String ddl = config.engine() == PersistenceEngine.SQLITE
				? "alter table twins add column ingestion_token text"
				: "alter table twins add column ingestion_token varchar(255)";
		try (Statement statement = connection.createStatement()) {
			statement.execute(ddl);
		} catch (SQLException e) {
			if (!isDuplicateColumnError(e)) throw e;
		}
	}

	private static boolean isDuplicateColumnError(SQLException error) {
		if (error == null) return false;
		String message = error.getMessage();
		if (message == null) return false;
		String normalized = message.toLowerCase(Locale.ROOT);
		return normalized.contains("duplicate column")
				|| normalized.contains("already exists")
				|| normalized.contains("duplicate name");
	}

	private void enableSqliteForeignKeys(Connection connection) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.execute("pragma foreign_keys = on");
		}
	}

	private <T> T readJson(String raw, Class<T> type, Supplier<T> fallback) {
		if (raw == null || raw.isBlank()) return fallback.get();
		try {
			return mapper.readValue(raw, type);
		} catch (IOException e) {
			throw new PersistenceException("Unable to deserialize persisted JSON payload", e);
		}
	}

	private <T> T readJson(String raw, com.fasterxml.jackson.core.type.TypeReference<T> typeRef, Supplier<T> fallback) {
		if (raw == null || raw.isBlank()) return fallback.get();
		try {
			return mapper.readValue(raw, typeRef);
		} catch (IOException e) {
			throw new PersistenceException("Unable to deserialize persisted JSON payload", e);
		}
	}

	private String writeJson(Object value) {
		try {
			return mapper.writeValueAsString(value == null ? Map.of() : value);
		} catch (IOException e) {
			throw new PersistenceException("Unable to serialize persisted JSON payload", e);
		}
	}

	private static Instant parseInstant(String raw) {
		if (raw == null || raw.isBlank()) return null;
		try {
			return Instant.parse(raw);
		} catch (Exception ignored) {
			return null;
		}
	}

	private static TwinType parseTwinType(String raw) {
		try {
			return raw == null ? TwinType.OTHER : TwinType.fromWireValue(raw.trim());
		} catch (Exception ignored) {
			return TwinType.OTHER;
		}
	}

	private static TwinStatus parseTwinStatus(String raw) {
		try {
			return raw == null ? TwinStatus.DRAFT : TwinStatus.fromWireValue(raw.trim());
		} catch (Exception ignored) {
			return TwinStatus.DRAFT;
		}
	}

	private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
		int value = rs.getInt(column);
		return rs.wasNull() ? null : value;
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	private static DigitalTwin withDatasets(DigitalTwin twin, List<SubjectDataset> datasets) {
		return new DigitalTwin(
				twin.id(),
				twin.name(),
				twin.description(),
				twin.version(),
				twin.image(),
				twin.type(),
				twin.status(),
				twin.updatedAt(),
				twin.creditsUsed(),
				twin.model(),
				twin.subjects(),
				twin.inferenceEngine(),
				datasets,
				twin.ingestionToken()
		);
	}

	private record LoadedTwinRow(String ownerUserId, DigitalTwin twin, List<SubjectDataset> datasets) {
		private LoadedTwinRow(String ownerUserId, DigitalTwin twin) {
			this(ownerUserId, twin, new ArrayList<>());
		}
	}
}
