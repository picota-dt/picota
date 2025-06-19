package io.picota.digitaltwin.actions;

import io.intino.alexandria.exceptions.AlexandriaException;
import io.intino.alexandria.exceptions.BadRequest;
import io.intino.alexandria.exceptions.Forbidden;
import io.intino.alexandria.exceptions.InternalServerError;
import io.intino.alexandria.logger.Logger;
import io.picota.digitaltwin.DigitalTwinBox;
import io.picota.digitaltwin.control.commands.CommandFactory;
import io.picota.digitaltwin.control.commands.EvaluateVariablesCommand;
import io.picota.digitaltwin.model.DigitalTwin;
import io.picota.digitaltwin.model.Inference;

import java.util.List;
import java.util.Map;

public class PostInferenceAction implements io.intino.alexandria.rest.RequestErrorHandler {
	public DigitalTwinBox box;
	public String subject;
	public Map values;
	public io.intino.alexandria.http.server.AlexandriaHttpContext context;
	public String id;

	public List<io.picota.digitaltwin.schemas.Inference> execute() throws BadRequest, InternalServerError {
		try {
			DigitalTwin digitalTwin = box.store().get(id);
			if (digitalTwin.token() != null) checkToken(digitalTwin);
			EvaluateVariablesCommand command = new CommandFactory(box).build(EvaluateVariablesCommand.class, id, subject, values);
			return command.execute().resource().stream().map(this::map).toList();
		} catch (Throwable e) {
			if (e instanceof IllegalArgumentException) throw new BadRequest(e.getMessage());
			Logger.error(e);
			throw new InternalServerError(e.getMessage());
		}
	}

	private void checkToken(DigitalTwin digitalTwin) throws Forbidden {
		String authorization = context.manager().getHeader("Authorization");
		if (authorization == null) throw new Forbidden("Required authentication");
		if (!authorization.startsWith("Bearer ")) throw new Forbidden("Required authentication");
		authorization = authorization.substring("Bearer ".length());
		if (!digitalTwin.token().equals(authorization)) throw new Forbidden("Invalid authentication");
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