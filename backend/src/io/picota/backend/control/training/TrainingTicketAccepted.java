package io.picota.backend.control.training;

import java.time.Instant;

public record TrainingTicketAccepted(
		String ticketId,
		String status,
		Instant createdAt
) {
}
