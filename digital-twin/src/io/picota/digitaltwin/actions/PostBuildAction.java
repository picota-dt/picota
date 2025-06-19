package io.picota.digitaltwin.actions;

import io.intino.alexandria.exceptions.AlexandriaException;
import io.intino.alexandria.exceptions.BadRequest;
import io.picota.digitaltwin.DigitalTwinBox;
import io.picota.digitaltwin.control.commands.CommandFactory;
import io.picota.digitaltwin.control.commands.ReadModelCommand;
import io.picota.digitaltwin.control.commands.TrainSubjectsCommand;


public class PostBuildAction implements io.intino.alexandria.rest.RequestErrorHandler {
	public DigitalTwinBox box;
	public io.intino.alexandria.http.server.AlexandriaHttpContext context;
	public String id;

	public void execute() throws BadRequest {
		CommandFactory factory = new CommandFactory(box);
		try {
			factory.build(ReadModelCommand.class, id).execute();
			factory.build(TrainSubjectsCommand.class, id).execute();
		} catch (Exception e) {
			throw new BadRequest(e.getMessage());
		}
	}

	public void onMalformedRequest(Throwable e) throws AlexandriaException {
		throw new BadRequest("Malformed request");
	}
}