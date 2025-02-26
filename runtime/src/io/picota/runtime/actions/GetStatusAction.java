package io.picota.runtime.actions;

import io.intino.alexandria.exceptions.AlexandriaException;
import io.intino.alexandria.exceptions.BadRequest;
import io.intino.datahub.model.Sensor;
import io.picota.runtime.RuntimeBox;
import io.picota.runtime.rest.resources.GetStatusResource;

import static io.picota.runtime.rest.resources.GetStatusResource.Moment.Future;


public class GetStatusAction implements io.intino.alexandria.rest.RequestErrorHandler {
	public io.picota.runtime.rest.resources.GetStatusResource.Moment moment;
	public String entity;
	public RuntimeBox box;
	public io.intino.alexandria.http.server.AlexandriaHttpContext context;

	public String execute() {
		Sensor sensor = box.entity(entity);
		String inference = getInference(sensor);
		return null;
	}

	public void onMalformedRequest(Throwable e) throws AlexandriaException {
		Sensor sensor = box.entity(entity);
		if (sensor == null) throw new BadRequest("Entity not found");
		if (moment.equals(Future))
			if (!GetStatusResource.Moment.Future.name().equals(getInference(sensor)))
				throw new BadRequest("Invalid moment");
		throw new BadRequest("Malformed request");
	}

	private static String getInference(Sensor sensor) {
		return sensor.attribute(a -> a.name$().equalsIgnoreCase("inference")).value();
	}
}