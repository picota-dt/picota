package io.picota.digitaltwin.actions;

import io.intino.alexandria.exceptions.AlexandriaException;
import io.intino.alexandria.exceptions.BadRequest;
import io.picota.digitaltwin.DigitalTwinBox.State;
import io.picota.digitaltwin.rest.resources.PostStateResource.Value;

import static io.quassar.picota.DigitalTwin.DigitalSubject;

public class PostStateAction implements io.intino.alexandria.rest.RequestErrorHandler {
	public io.picota.digitaltwin.DigitalTwinBox box;
	public String digitalSubject;
	public io.intino.alexandria.http.server.AlexandriaHttpContext context;
	public Value value;

	public void execute() throws BadRequest {
		if (value == null || digitalSubject == null) throw new BadRequest("Missing required parameter");
		DigitalSubject subject = box.digitalSubject(digitalSubject);
		if (value == Value.Waiting) {
			if (box.state(subject) == State.Training) box.dtBuilder().stop();
			box.state(subject, State.WaitingData);
		} else if (value == Value.Training) {
			if (box.state(subject) == State.Training) throw new BadRequest("Already training");
			else {
				box.state(subject, State.Training);
//				Future<?> training = box.dtBuilder().build(dt, result -> onfinish(dt, result));
			}
		} else if (value == Value.Operating) {
			if (box.state(subject) == State.WaitingData) throw new BadRequest("Currently waiting for training");
			if (box.state(subject) == State.Operating) throw new BadRequest("Already operating");
			if (box.state(subject) == State.Training) throw new BadRequest("Currently training");
			box.state(subject, State.Operating);
		}
	}

	public void onMalformedRequest(Throwable e) throws AlexandriaException {
		throw new BadRequest("Malformed request: " + e.getMessage());
	}
}