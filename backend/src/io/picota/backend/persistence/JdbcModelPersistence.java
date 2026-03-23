package io.picota.backend.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.picota.backend.model.ApplicationModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.Properties;

public class JdbcModelPersistence implements ModelPersistence {
	private static final int STATE_ROW_ID = 1;

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
	public Optional<ApplicationModel> loadModel() {
		String sql = "select payload from app_model_state where id = ?";
		try (Connection connection = openConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setInt(1, STATE_ROW_ID);
			try (ResultSet rs = statement.executeQuery()) {
				if (!rs.next()) return Optional.empty();
				String payload = rs.getString("payload");
				if (payload == null || payload.isBlank()) return Optional.of(ApplicationModel.empty());
				return Optional.of(mapper.readValue(payload, ApplicationModel.class));
			}
		} catch (SQLException | IOException e) {
			throw new PersistenceException("Unable to load application model from database", e);
		}
	}

	@Override
	public void saveModel(ApplicationModel model) {
		ApplicationModel safeModel = model == null ? ApplicationModel.empty() : model;
		String payload;
		try {
			payload = mapper.writeValueAsString(safeModel);
		} catch (IOException e) {
			throw new PersistenceException("Unable to serialize application model", e);
		}

		String sql = config.engine() == PersistenceEngine.SQLITE
				? "insert into app_model_state (id, payload, updated_at) values (?, ?, current_timestamp) " +
				"on conflict(id) do update set payload = excluded.payload, updated_at = current_timestamp"
				: "insert into app_model_state (id, payload, updated_at) values (?, ?, current_timestamp) " +
				"on duplicate key update payload = values(payload), updated_at = current_timestamp";

		try (Connection connection = openConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setInt(1, STATE_ROW_ID);
			statement.setString(2, payload);
			statement.executeUpdate();
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
		String sql = config.engine() == PersistenceEngine.SQLITE
				? "create table if not exists app_model_state (" +
				"id integer primary key check(id = 1), " +
				"payload text not null, " +
				"updated_at text not null default current_timestamp" +
				")"
				: "create table if not exists app_model_state (" +
				"id int primary key, " +
				"payload longtext not null, " +
				"updated_at timestamp not null default current_timestamp on update current_timestamp" +
				") engine=InnoDB default charset=utf8mb4";

		try (Connection connection = openConnection();
			 Statement statement = connection.createStatement()) {
			statement.execute(sql);
		} catch (SQLException e) {
			throw new PersistenceException("Unable to initialize persistence schema", e);
		}
	}

	private Connection openConnection() throws SQLException {
		if (config.engine() == PersistenceEngine.SQLITE) {
			return DriverManager.getConnection(config.jdbcUrl());
		}
		if (config.mysqlUser().isBlank() && config.mysqlPassword().isBlank()) {
			return DriverManager.getConnection(config.jdbcUrl());
		}
		Properties props = new Properties();
		if (!config.mysqlUser().isBlank()) props.setProperty("user", config.mysqlUser());
		if (!config.mysqlPassword().isBlank()) props.setProperty("password", config.mysqlPassword());
		return DriverManager.getConnection(config.jdbcUrl(), props);
	}
}
