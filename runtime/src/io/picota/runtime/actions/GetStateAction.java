package io.picota.runtime.actions;

import io.picota.runtime.RuntimeBox;
import io.intino.alexandria.exceptions.*;
import java.time.*;
import java.util.*;


public class GetStateAction implements io.intino.alexandria.rest.RequestErrorHandler {
	public String entity;
	public RuntimeBox box;
	public io.intino.alexandria.http.server.AlexandriaHttpContext context;

	public Enum execute() {
		return box.state();
	}

	public void onMalformedRequest(Throwable e) throws AlexandriaException {
		throw new BadRequest("Malformed request");
	}
}