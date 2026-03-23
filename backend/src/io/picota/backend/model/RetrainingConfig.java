package io.picota.backend.model;

public record RetrainingConfig(
		Boolean enabled,
		RetrainingSchedule schedule,
		Integer minNewRecords,
		String timeOfDay
) {
}
