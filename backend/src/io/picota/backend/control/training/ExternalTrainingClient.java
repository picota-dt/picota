package io.picota.backend.control.training;

import java.util.Map;

public interface ExternalTrainingClient {
	TrainingTicketAccepted createTraining(Map<String, Object> request);

	TrainingTicketSnapshot getTraining(String ticketId);

	TrainingInferenceResult createInference(Map<String, Object> request);

	static ExternalTrainingClient disabled() {
		return new ExternalTrainingClient() {
			@Override
			public TrainingTicketAccepted createTraining(Map<String, Object> request) {
				throw new TrainingApiException("Training API is not configured");
			}

			@Override
			public TrainingTicketSnapshot getTraining(String ticketId) {
				throw new TrainingApiException("Training API is not configured");
			}

			@Override
			public TrainingInferenceResult createInference(Map<String, Object> request) {
				throw new TrainingApiException("Training API is not configured");
			}
		};
	}
}
