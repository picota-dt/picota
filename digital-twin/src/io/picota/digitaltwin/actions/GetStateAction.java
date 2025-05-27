package io.picota.digitaltwin.actions;

import io.intino.alexandria.exceptions.*;

public class GetStateAction implements io.intino.alexandria.rest.RequestErrorHandler {
	public io.picota.digitaltwin.DigitalTwinBox box;
	public String digitalSubject;
	public String digitalTwin;
	public io.intino.alexandria.http.server.AlexandriaHttpContext context;

	public Enum execute() throws BadRequest {
		if (box.digitalSubject(digitalTwin) == null) throw new BadRequest("Digital Twin not found");
		return box.state(box.digitalSubject(digitalTwin));
	}

	public void onMalformedRequest(Throwable e) throws AlexandriaException {
		throw new BadRequest("Malformed request");
	}
}