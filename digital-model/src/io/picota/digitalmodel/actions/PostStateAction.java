package io.picota.digitalmodel.actions;

import io.intino.alexandria.exceptions.AlexandriaException;
import io.intino.alexandria.exceptions.BadRequest;
import io.picota.digitalmodel.DigitalModelBox;
import io.picota.digitalmodel.DigitalModelBox.State;
import io.picota.digitalmodel.DigitalTwinBuilder;
import io.picota.digitalmodel.TrainReportBuilder;
import io.picota.digitalmodel.rest.resources.PostStateResource.Value;
import model.DigitalTwin;

import java.io.File;

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

	private void onfinish(DigitalTwin dt, DigitalTwinBuilder.Result result) {
		System.out.println(result.report());
		createPDF(result);
		box.lastTraining(dt, result.trainings());
		box.state(dt, State.Prepared);
	}

	private void createPDF(DigitalTwinBuilder.Result result) {
		new TrainReportBuilder(result.trainings(), result.report()).save(new File(box.configuration().home(), "reports"));
	}

	public void onMalformedRequest(Throwable e) throws AlexandriaException {
		throw new BadRequest("Malformed request: " + e.getMessage());
	}
}