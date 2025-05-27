package io.picota.digitaltwin.actions;

import io.intino.alexandria.exceptions.AlexandriaException;
import io.intino.alexandria.exceptions.BadRequest;
import io.intino.alexandria.exceptions.InternalServerError;
import io.intino.alexandria.http.server.AlexandriaHttpContext;
import io.intino.alexandria.rest.RequestErrorHandler;
import io.quassar.DigitalTwin;

import java.util.HashMap;
import java.util.Map;


public class GetStatusAction implements RequestErrorHandler {
	public io.picota.digitaltwin.DigitalTwinBox box;
	public String digitalSubject;
	public AlexandriaHttpContext context;
	public String digitalTwin;

	public Map execute() throws BadRequest, InternalServerError {
		Map<String, Object> map = new HashMap<>();
//		var subject = box.vault().open(subject);
//		if (subject == null) throw new InternalServerError("Data not found");
//		var dt = box.subject(subject);
//		if (mode == Future) variables(dt).forEach(a -> map.put(a.name$(), value(a, subject)));
//		List<DigitalTwinOperator.Inference> infer = box.dtOperator().infer(dt);
//		infer.forEach(i -> map.put(i.variable(), i.value()));
		//TODO
		return map;
	}

	public void onMalformedRequest(Throwable e) throws AlexandriaException {
		DigitalTwin.DigitalSubject s = box.digitalSubject(digitalTwin);
		if (s == null) throw new BadRequest("Subject not found");
	}
}