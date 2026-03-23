package io.picota.backend.persistence;

public final class PersistenceFactory {
	private PersistenceFactory() {
	}

	public static ModelPersistence create(PersistenceConfig config) {
		return new JdbcModelPersistence(config);
	}
}
