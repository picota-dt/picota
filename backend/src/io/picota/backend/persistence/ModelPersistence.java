package io.picota.backend.persistence;

import io.picota.backend.model.Application;

import java.util.Optional;

public interface ModelPersistence extends AutoCloseable {
	Optional<Application> loadModel();

	void saveModel(Application model);

	@Override
	void close();
}
