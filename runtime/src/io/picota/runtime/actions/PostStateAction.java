package io.picota.runtime.actions;

import io.intino.alexandria.exceptions.AlexandriaException;
import io.intino.alexandria.exceptions.BadRequest;
import io.intino.alexandria.logger.Logger;
import io.picota.runtime.RuntimeBox;
import io.picota.runtime.RuntimeBox.State;
import io.picota.runtime.rest.resources.PostStateResource;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


public class PostStateAction implements io.intino.alexandria.rest.RequestErrorHandler {
	public String entity;
	public RuntimeBox box;
	public io.intino.alexandria.http.server.AlexandriaHttpContext context;
	public PostStateResource.Value value;
	private Future<?> training;

	public void execute() throws BadRequest {
		if (value == null || entity == null) throw new BadRequest("Missing required parameter");
		if (value == PostStateResource.Value.Waiting) {
			if (box.state() == State.Training) box.dtBuilder().stop();
			else if (box.state() == State.Operating) box.datahub().stop();
			box.state(State.Waiting);
		} else if (value == PostStateResource.Value.Training) {
			if (box.state() == State.Training) throw new BadRequest("Already training");
			if (box.state() == State.Waiting || box.state() == State.Operating) {
				box.datahub().stop();
				training = box.dtBuilder().start(System.out::println);
				subscribeToFinished(training);
				box.state(State.Training);
			}
		} else if (value == PostStateResource.Value.Operating) {
			if (box.state() == State.Waiting) throw new BadRequest("Currently waiting for training");
			if (box.state() == State.Operating) throw new BadRequest("Already operating");
			if (box.state() == State.Training) throw new BadRequest("Currently training");
			if (box.state() == State.Prepared) box.datahub().start();
			box.dtEvaluator().start();
		}
	}

	public Future<?> training() {
		return training;
	}

	private void subscribeToFinished(Future<?> start) {
		new Thread(() -> {
			try {
				start.get(1, TimeUnit.HOURS);
				box.state(State.Prepared);
			} catch (InterruptedException | ExecutionException | TimeoutException e) {
				Logger.error(e);
			}
		});
	}

	public void onMalformedRequest(Throwable e) throws AlexandriaException {
		throw new BadRequest("Malformed request");
	}
}