package io.picota.backend.control.ui;

public record RetrainingConfig(
		Boolean enabled,
		RetrainingSchedule schedule,
		Integer minNewRecords,
		String timeOfDay
) {
}
