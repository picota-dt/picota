package io.picota.runtime.actions;

import io.intino.alexandria.exceptions.AlexandriaException;
import io.intino.alexandria.exceptions.BadRequest;
import io.picota.runtime.DigitalTwinBuilder;
import io.picota.runtime.DigitalTwinBuilder.Result.Training;
import io.picota.runtime.RuntimeBox;
import io.picota.runtime.RuntimeBox.State;
import io.picota.runtime.rest.resources.PostStateResource;
import io.picota.runtime.rest.resources.PostStateResource.Value;

import java.util.List;
import java.util.concurrent.Future;


public class PostStateAction implements io.intino.alexandria.rest.RequestErrorHandler {
	public String entity;
	public RuntimeBox box;
	public io.intino.alexandria.http.server.AlexandriaHttpContext context;
	public Value value;
	private Future<?> training;

	public void execute() throws BadRequest {
		if (value == null || entity == null) throw new BadRequest("Missing required parameter");
		if (value == Value.Waiting) {
			if (box.state() == State.Training) box.dtBuilder().stop();
			else if (box.state() == State.Operating) box.datahub().stop();
			box.state(State.Waiting);
		} else if (value == Value.Training) {
			if (box.state() == State.Training) throw new BadRequest("Already training");
			else {
				box.stopDatahub();
				training = box.dtBuilder().start(this::onfinish);
				box.state(State.Training);
			}
		} else if (value == Value.Operating) {
			if (box.state() == State.Waiting) throw new BadRequest("Currently waiting for training");
			if (box.state() == State.Operating) throw new BadRequest("Already operating");
			if (box.state() == State.Training) throw new BadRequest("Currently training");
			if (box.state() == State.Prepared) box.startDatahub();
			box.dtOperator().start();
			box.state(State.Operating);
		}
	}

	private void onfinish(DigitalTwinBuilder.Result result) {
		System.out.println(result.report());
		publishTraining(result.trainings());
		box.state(State.Prepared);
	}



	private void publishTraining(List<Training> trainings) {
		//toDO
	}

	public Future<?> training() {
		return training;
	}

	public void onMalformedRequest(Throwable e) throws AlexandriaException {
		throw new BadRequest("Malformed request: " + e.getMessage());
	}
}