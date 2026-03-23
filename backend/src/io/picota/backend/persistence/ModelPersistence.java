package io.picota.backend.persistence;

import io.picota.backend.model.ApplicationModel;

import java.util.Optional;

public interface ModelPersistence extends AutoCloseable {
	Optional<ApplicationModel> loadModel();

	void saveModel(ApplicationModel model);

	@Override
	void close();
}
