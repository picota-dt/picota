package io.picota.digitaltwin.actions;

import io.intino.alexandria.exceptions.AlexandriaException;
import io.intino.alexandria.exceptions.BadRequest;
import io.intino.alexandria.logger.Logger;
import io.picota.digitaltwin.DigitalTwinBox;
import io.picota.digitaltwin.control.commands.Command;
import io.picota.digitaltwin.control.commands.CommandFactory;
import io.picota.digitaltwin.control.commands.EvaluateVariablesCommand;

import java.util.Map;


public class PostInferenceAction implements io.intino.alexandria.rest.RequestErrorHandler {
	public Map values;
	public DigitalTwinBox box;
	public io.intino.alexandria.http.server.AlexandriaHttpContext context;
	public String id;

	public void execute() throws BadRequest {
		try {
			EvaluateVariablesCommand command = new CommandFactory(box).build(EvaluateVariablesCommand.class, id, values);
			Command.Result result = command.execute();
		} catch (Throwable e) {
			Logger.error(e);
			if (e instanceof IllegalArgumentException) throw new BadRequest(e.getMessage());
		}
	}

	public void onMalformedRequest(Throwable e) throws AlexandriaException {
		throw new BadRequest("Malformed request");
	}
}