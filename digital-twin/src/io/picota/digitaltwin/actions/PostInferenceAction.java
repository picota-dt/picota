package io.picota.digitaltwin.actions;

import io.intino.alexandria.exceptions.AlexandriaException;
import io.intino.alexandria.exceptions.BadRequest;
import io.intino.alexandria.exceptions.InternalServerError;
import io.intino.alexandria.logger.Logger;
import io.picota.digitaltwin.DigitalTwinBox;
import io.picota.digitaltwin.control.commands.Command;
import io.picota.digitaltwin.control.commands.CommandFactory;
import io.picota.digitaltwin.control.commands.EvaluateVariablesCommand;
import io.picota.digitaltwin.model.Inference;

import java.util.List;
import java.util.Map;


public class PostInferenceAction implements io.intino.alexandria.rest.RequestErrorHandler {
	public Map values;
	public DigitalTwinBox box;
	public io.intino.alexandria.http.server.AlexandriaHttpContext context;
	public String id;

	public java.util.List<io.picota.digitaltwin.schemas.Inference> execute() throws BadRequest, InternalServerError {
		try {
			EvaluateVariablesCommand command = new CommandFactory(box).build(EvaluateVariablesCommand.class, id, values);
			Command.Result result = command.execute();
			List<Inference> inferences = (List<Inference>) result.resource();
			return inferences.stream().map(this::map).toList();
		} catch (Throwable e) {
			Logger.error(e);
			if (e instanceof IllegalArgumentException) throw new BadRequest(e.getMessage());
			throw new InternalServerError(e.getMessage());
		}
	}

	private io.picota.digitaltwin.schemas.Inference map(Inference i) {
		return new io.picota.digitaltwin.schemas.Inference()
				.subject(i.subject().name$())
				.variable(i.variable())
				.value(i.value());
	}

	public void onMalformedRequest(Throwable e) throws AlexandriaException {
		throw new BadRequest("Malformed request");
	}
}