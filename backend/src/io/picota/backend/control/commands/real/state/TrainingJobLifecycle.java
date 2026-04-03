package io.picota.backend.control.commands.real.state;

import io.picota.backend.control.training.TrainingTicketSnapshot;
import io.picota.backend.control.ui.schemas.TrainingJob;
import io.picota.backend.control.ui.schemas.TrainingJobStatus;

import java.time.Instant;
import java.util.Optional;

final class TrainingJobLifecycle {

	TrainingJobStatus mapExternalStatus(String externalStatus, TrainingJob current) {
		if (externalStatus == null || externalStatus.isBlank()) {
			return current == null || current.status() == null ? TrainingJobStatus.QUEUED : current.status();
		}
		String normalized = externalStatus.trim().toLowerCase();
		return switch (normalized) {
			case "queued" -> current == null ? TrainingJobStatus.QUEUED : TrainingJobStatus.PREPARING;
			case "running" -> current != null && current.status() == TrainingJobStatus.PREPARING
					? TrainingJobStatus.TRAINING
					: current != null && current.status() == TrainingJobStatus.QUEUED
					  ? TrainingJobStatus.PREPARING
					  : TrainingJobStatus.TRAINING;
			case "completed" -> TrainingJobStatus.DONE;
			case "failed" -> TrainingJobStatus.FAILED;
			default -> current == null || current.status() == null ? TrainingJobStatus.QUEUED : current.status();
		};
	}

	Integer resolveProgress(TrainingJobStatus status, Integer currentProgress, TrainingTicketSnapshot snapshot) {
		int current = currentProgress == null ? 0 : currentProgress;
		Integer epochsProgress = progressFromEpochs(snapshot);
		return switch (status) {
			case DONE -> 100;
			case FAILED -> epochsProgress != null ? clamp(epochsProgress, 0, 100) : (current > 0 ? current : 100);
			case QUEUED -> Math.max(current, 5);
			case PREPARING -> clamp(Math.max(current + 8, 15), 10, 35);
			case TRAINING -> epochsProgress == null
					? clamp(Math.max(current + 12, 40), 35, 92)
					: clamp(Math.max(epochsProgress, current), 0, 99);
			case EVALUATING -> epochsProgress == null
					? clamp(Math.max(current + 6, 93), 90, 98)
					: clamp(Math.max(epochsProgress, 95), 90, 99);
		};
	}

	String phaseForSnapshot(TrainingJobStatus status, TrainingTicketSnapshot snapshot) {
		if (status == TrainingJobStatus.FAILED) return "Training failed";
		Optional<String> epochLabel = epochLabel(snapshot);
		if (status == TrainingJobStatus.TRAINING && epochLabel.isPresent()) {
			return "Training epoch " + epochLabel.get() + "...";
		}
		if (status == TrainingJobStatus.EVALUATING && epochLabel.isPresent()) {
			return "Evaluating model... (" + epochLabel.get() + ")";
		}
		return phaseForStatus(status);
	}

	String phaseForStatus(TrainingJobStatus status) {
		return switch (status) {
			case QUEUED -> "Queued";
			case PREPARING -> "Preparing dataset...";
			case TRAINING -> "Training in progress...";
			case EVALUATING -> "Evaluating model...";
			case DONE -> "Training complete";
			case FAILED -> "Training failed";
		};
	}

	Instant selectStartedAt(TrainingJob current, TrainingTicketSnapshot snapshot, TrainingJobStatus nextStatus) {
		if (snapshot.startedAt() != null) return snapshot.startedAt();
		if (current.startedAt() != null) return current.startedAt();
		if (nextStatus == TrainingJobStatus.TRAINING
				|| nextStatus == TrainingJobStatus.EVALUATING
				|| nextStatus == TrainingJobStatus.DONE
				|| nextStatus == TrainingJobStatus.FAILED) {
			return Instant.now();
		}
		return null;
	}

	Instant selectCompletedAt(TrainingJob current, TrainingTicketSnapshot snapshot) {
		if (snapshot.finishedAt() != null) return snapshot.finishedAt();
		if (current.completedAt() != null) return current.completedAt();
		return Instant.now();
	}

	boolean isTerminal(TrainingJobStatus status) {
		return status == TrainingJobStatus.DONE || status == TrainingJobStatus.FAILED;
	}

	private Integer progressFromEpochs(TrainingTicketSnapshot snapshot) {
		if (snapshot == null) return null;
		if (snapshot.progressPercent() != null && Double.isFinite(snapshot.progressPercent())) {
			return (int) Math.round(snapshot.progressPercent());
		}
		Integer completed = snapshot.epochsCompleted();
		Integer total = snapshot.epochsTotal();
		if (completed == null || total == null || total <= 0) return null;
		return (int) Math.round((completed * 100.0) / total);
	}

	private Optional<String> epochLabel(TrainingTicketSnapshot snapshot) {
		if (snapshot == null) return Optional.empty();
		Integer completed = snapshot.epochsCompleted();
		Integer total = snapshot.epochsTotal();
		if (completed == null || total == null || total <= 0) return Optional.empty();
		return Optional.of(clamp(completed, 0, total) + "/" + total);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
