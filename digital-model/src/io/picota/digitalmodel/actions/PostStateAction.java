package io.picota.digitalmodel.actions;

import io.intino.alexandria.exceptions.AlexandriaException;
import io.intino.alexandria.exceptions.BadRequest;
import io.picota.digitalmodel.DigitalModelBox;
import io.picota.digitalmodel.DigitalModelBox.State;
import io.picota.digitalmodel.rest.resources.PostStateResource.Value;
import model.DigitalTwin;

public class PostStateAction implements io.intino.alexandria.rest.RequestErrorHandler {
	public DigitalModelBox box;
	public io.intino.alexandria.http.server.AlexandriaHttpContext context;
	public String digitalTwin;
	public Value value;

	public void execute() throws BadRequest {
		if (value == null || digitalTwin == null) throw new BadRequest("Missing required parameter");
		DigitalTwin dt = box.digitalTwin(digitalTwin);
		if (value == Value.Waiting) {
			if (box.state(dt) == State.Training) box.dtBuilder().stop();
			box.state(dt, State.WaitingData);
		} else if (value == Value.Training) {
			if (box.state(dt) == State.Training) throw new BadRequest("Already training");
			else {
				box.state(dt, State.Training);
//				Future<?> training = box.dtBuilder().build(dt, result -> onfinish(dt, result));
			}
		} else if (value == Value.Operating) {
			if (box.state(dt) == State.WaitingData) throw new BadRequest("Currently waiting for training");
			if (box.state(dt) == State.Operating) throw new BadRequest("Already operating");
			if (box.state(dt) == State.Training) throw new BadRequest("Currently training");
			box.state(dt, State.Operating);
		}
	}

	public void onMalformedRequest(Throwable e) throws AlexandriaException {
		throw new BadRequest("Malformed request: " + e.getMessage());
	}
}