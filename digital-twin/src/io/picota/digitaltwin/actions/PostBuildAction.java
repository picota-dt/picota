package io.picota.digitaltwin.actions;

import io.picota.digitaltwin.DigitalTwinBox;
import io.intino.alexandria.exceptions.*;
import java.time.*;
import java.util.*;


public class PostBuildAction implements io.intino.alexandria.rest.RequestErrorHandler {
	public DigitalTwinBox box;
	public io.intino.alexandria.http.server.AlexandriaHttpContext context;
	public String id;

	public void execute() throws BadRequest {

	}

	public void onMalformedRequest(Throwable e) throws AlexandriaException {
		//TODO
		throw new BadRequest("Malformed request");
	}
}