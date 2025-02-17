package io.picota.runtime.actions;

import io.intino.alexandria.exceptions.AlexandriaException;
import io.intino.alexandria.exceptions.BadRequest;
import io.picota.runtime.RuntimeBox;
import io.picota.runtime.RuntimeBox.State;
import io.picota.runtime.rest.resources.PostStateResource;


public class PostStateAction implements io.intino.alexandria.rest.RequestErrorHandler {
	public String entity;
	public RuntimeBox box;
	public io.intino.alexandria.http.server.AlexandriaHttpContext context;
	public PostStateResource.Value value;

	public void execute() throws BadRequest {
		if (value == PostStateResource.Value.Waiting) {
			if (box.state() == State.Training) box.dtBuilder().stop();
			else if (box.state() == State.Operating) box.datahub().stop();
			box.state(State.Waiting);
		} else if (value == PostStateResource.Value.Training) {
			if (box.state() == State.Training) throw new BadRequest("Already training");
			if (box.state() == State.Operating) {
				box.datahub().stop();
				box.dtBuilder().start();
				box.state(State.Training);
			}
		} else if (value == PostStateResource.Value.Operating) {
			if (box.state() == State.Waiting) throw new BadRequest("Currently waiting for training");
			if (box.state() == State.Operating) throw new BadRequest("Already operating");
			if (box.state() == State.Training) throw new BadRequest("Currently training");
			if (box.state() == State.Prepared) box.datahub().start();
			//TODO start inferential endpoint
		}
	}

	public void onMalformedRequest(Throwable e) throws AlexandriaException {
		throw new BadRequest("Malformed request");
	}
}