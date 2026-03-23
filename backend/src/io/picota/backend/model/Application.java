package io.picota.backend.model;

import java.util.List;

public record Application(
		List<UserAccount> users,
		List<UserSession> sessions,
		List<TwinAggregate> twins,
		List<TrainingJobAggregate> trainingJobs
) {
	public Application {
		users = users == null ? List.of() : List.copyOf(users);
		sessions = sessions == null ? List.of() : List.copyOf(sessions);
		twins = twins == null ? List.of() : List.copyOf(twins);
		trainingJobs = trainingJobs == null ? List.of() : List.copyOf(trainingJobs);
	}

	public static Application empty() {
		return new Application(List.of(), List.of(), List.of(), List.of());
	}
}
