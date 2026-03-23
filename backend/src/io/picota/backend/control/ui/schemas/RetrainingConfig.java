package io.picota.backend.control.ui.schemas;

public record RetrainingConfig(
		Boolean enabled,
		RetrainingSchedule schedule,
		Integer minNewRecords,
		String timeOfDay
) {
}
