package io.picota.digitalmodel.actions;

import io.picota.digitalmodel.DigitalModelBox;
import io.intino.alexandria.exceptions.*;

public class GetStateAction implements io.intino.alexandria.rest.RequestErrorHandler {
	public DigitalModelBox box;
	public String digitalTwin;
	public io.intino.alexandria.http.server.AlexandriaHttpContext context;

	public Enum execute() throws BadRequest {
		if (box.digitalTwin(digitalTwin) == null) throw new BadRequest("Digital Twin not found");
		return box.state(box.digitalTwin(digitalTwin));
	}

	public void onMalformedRequest(Throwable e) throws AlexandriaException {
		throw new BadRequest("Malformed request");
	}
}