package io.picota.digitaltwin.control.commands;

import io.intino.alexandria.Resource;
import io.intino.alexandria.logger.Logger;
import io.picota.digitaltwin.DigitalTwinBox;
import io.picota.digitaltwin.control.utils.Utils;
import io.picota.digitaltwin.model.DigitalTwin;
import io.picota.digitaltwin.model.DigitalTwin.State;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class BuildModelCommand implements Command<Void> {
	private final DigitalTwinBox box;
	private final String digitalTwinId;
	private final String notifyEmail;
	private final Resource resource;
	private final CommandFactory factory;

	public BuildModelCommand(DigitalTwinBox box, String digitalTwinId, String notifyEmail, Resource resource) {
		this.box = box;
		this.digitalTwinId = digitalTwinId;
		this.notifyEmail = notifyEmail;
		this.resource = resource;
		this.factory = new CommandFactory(box);
	}

	@Override
	public Result<Void> execute() {
		DigitalTwin digitalTwin = box.store().get(digitalTwinId);
		if (digitalTwin == null) throw new IllegalArgumentException("Digital Twin not found");
		if (notifyEmail != null && !notifyEmail.isEmpty()) digitalTwin.notifyEmail(notifyEmail);
		digitalTwin.state(State.DownloadedData);
		Future<Result<Void>> future = Utils.createExecutor("train").submit(() -> {
			try {
				Result<Void> result = factory.build(DownloadDataCommand.class, digitalTwinId, resource).execute();
				if (!result.success()) return result;
				else return factory.build(TrainSubjectsCommand.class, digitalTwinId).execute();
			} catch (IllegalArgumentException e) {
				digitalTwin.state(State.TrainFinished);
				return new Result<>(false, e.getMessage());
			} catch (Throwable e) {
				Logger.error(e);
				digitalTwin.state(State.TrainFinished);
				return new Result<>(false, e.getMessage());
			}
		});
		digitalTwin.trainProcess(future);
		if (!future.isDone()) {
			return defaultResult();
		} else try {
			return future.get();
		} catch (InterruptedException | ExecutionException e) {
			return defaultResult();
		}
	}

	private static Result<Void> defaultResult() {
		return new Result<>(true, "Building Digital Twin...");
	}
}
