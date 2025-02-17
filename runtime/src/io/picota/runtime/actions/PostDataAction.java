package io.picota.runtime.actions;

import io.intino.alexandria.logger.Logger;
import io.intino.datahub.box.actions.RecreateDatamartAction;
import io.picota.runtime.DataFeeder;
import io.picota.runtime.RuntimeBox;
import io.intino.alexandria.exceptions.*;

import java.io.IOException;
import java.time.*;
import java.util.*;

public class PostDataAction implements io.intino.alexandria.rest.RequestErrorHandler {
	public String entity;
	public RuntimeBox box;
	public io.intino.alexandria.http.server.AlexandriaHttpContext context;
	public io.intino.alexandria.Resource data;

	public void execute() throws BadRequest {
		try {
			DataFeeder feeder = new DataFeeder(box);
			feeder.feed(entity, context.get("content-type"), data.stream());
			remountDatamart();
		} catch (IOException e) {
			Logger.error(e);
		} catch (Exception e) {
			throw new BadRequest(e.getMessage());
		}
	}

	private void remountDatamart() {
		RecreateDatamartAction action = new RecreateDatamartAction();
		action.box = box.datahub();
		action.datamartName="all";
		action.execute();
	}

	public void onMalformedRequest(Throwable e) throws AlexandriaException {
		throw new BadRequest("Malformed request");
	}
}