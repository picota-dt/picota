package io.picota.digitalmodel.actions;

import io.intino.alexandria.exceptions.AlexandriaException;
import io.intino.alexandria.exceptions.BadRequest;
import io.intino.alexandria.http.server.AlexandriaHttpContext;
import io.intino.alexandria.rest.RequestErrorHandler;
import io.picota.digitalmodel.DigitalModelBox;
import io.picota.digitalmodel.DigitalTwinOperator;
import io.picota.digitalmodel.rest.resources.GetStatusResource;
import io.picota.language.model.DigitalTwin;
import io.picota.language.model.Variable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.picota.digitalmodel.rest.resources.GetStatusResource.Moment.Future;

public class GetStatusAction implements RequestErrorHandler {
	public DigitalModelBox box;
	public AlexandriaHttpContext context;
	public GetStatusResource.Moment moment;
	public String digitalTwin;

	public Map execute() {
		GetStatusResource.Moment mode = getInferenceType(box.digitalTwin(digitalTwin));
		Map<String, Object> map = new HashMap<>();
		var subject = box.subject(digitalTwin);
		var dt = box.digitalTwin(digitalTwin);
		if (mode == Future) variables(dt).forEach(a -> map.put(a.name$(), value(a, subject)));
		List<DigitalTwinOperator.Inference> infer = box.dtOperator().infer(dt);
		infer.forEach(i -> map.put(i.variable(), i.value()));
		return map;
	}

	private static String value(Variable a, systems.intino.datamarts.subjectstore.model.Subject store) {
		if (a.isEnumerated()) return store.history().current().text(a.name$());
		return store.history().current().text(a.name$());
	}

	private static Stream<Variable> variables(DigitalTwin definition) {
		return definition.subject().variableList().stream();
	}

	public void onMalformedRequest(Throwable e) throws AlexandriaException {
		DigitalTwin s = box.digitalTwin(digitalTwin);
		if (s == null) throw new BadRequest("Subject not found");
		if (moment.equals(Future))
			if (getInferenceType(s) == Future) throw new BadRequest("Invalid moment");
		throw new BadRequest("Malformed request");
	}

	private GetStatusResource.Moment getInferenceType(DigitalTwin s) {
		return s.isEstimate() ? GetStatusResource.Moment.Current : Future;
	}
}