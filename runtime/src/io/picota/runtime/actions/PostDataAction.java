package io.picota.runtime.actions;

import io.intino.alexandria.Context;
import io.intino.alexandria.exceptions.AlexandriaException;
import io.intino.alexandria.exceptions.BadRequest;
import io.intino.alexandria.logger.Logger;
import io.intino.datahub.box.actions.RecreateDatamartAction;
import io.picota.runtime.DataFeeder;
import io.picota.runtime.RuntimeBox;
import io.picota.runtime.rest.resources.PostDataResource;

import java.io.IOException;

public class PostDataAction implements io.intino.alexandria.rest.RequestErrorHandler {
	public PostDataResource.Type type;
	public String entity;
	public RuntimeBox box;
	public Context context;
	public io.intino.alexandria.Resource data;

	public void execute() throws BadRequest {
		try {
			new DataFeeder(box).feed(entity, type.name(), data.stream());
			remountDatamart();
		} catch (IOException e) {
			Logger.error(e);
		} catch (Exception e) {
			Logger.error(e);
			throw new BadRequest(e.getMessage());
		}
	}

	private void remountDatamart() {
		RecreateDatamartAction action = new RecreateDatamartAction();
		action.box = box.datahub();
		action.datamartName = "all";
		action.execute();
	}

	public void onMalformedRequest(Throwable e) throws AlexandriaException {
		throw new BadRequest("Malformed request");
	}
}