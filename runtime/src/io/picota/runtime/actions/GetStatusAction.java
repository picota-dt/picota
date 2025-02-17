package io.picota.runtime.actions;

import io.picota.runtime.RuntimeBox;
import io.intino.alexandria.exceptions.*;
import java.time.*;
import java.util.*;


public class GetStatusAction implements io.intino.alexandria.rest.RequestErrorHandler {
	public String entity;
	public RuntimeBox box;
	public io.intino.alexandria.http.server.AlexandriaHttpContext context;

	public String execute() {
		return null;
	}

	public void onMalformedRequest(Throwable e) throws AlexandriaException {
		//TODO
		throw new BadRequest("Malformed request");
	}
}