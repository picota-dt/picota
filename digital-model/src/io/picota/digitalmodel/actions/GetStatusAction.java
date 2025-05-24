package io.picota.digitalmodel.actions;

import io.intino.alexandria.exceptions.AlexandriaException;
import io.intino.alexandria.exceptions.BadRequest;
import io.intino.alexandria.exceptions.InternalServerError;
import io.intino.alexandria.http.server.AlexandriaHttpContext;
import io.intino.alexandria.rest.RequestErrorHandler;
import io.picota.digitalmodel.DigitalModelBox;
import io.picota.digitalmodel.DigitalTwinOperator;
import io.picota.digitalmodel.rest.resources.GetStatusResource;
import model.DigitalTwin;
import model.Variable;
import systems.intino.datamarts.subjectstore.SubjectHistory;

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

	public Map execute() throws BadRequest, InternalServerError {
		GetStatusResource.Moment mode = getInferenceType(box.digitalTwin(digitalTwin));
		Map<String, Object> map = new HashMap<>();
		var subject = box.vault().open(digitalTwin);
		if (subject == null) throw new InternalServerError("Data not found");
		var dt = box.digitalTwin(digitalTwin);
		if (mode == Future) variables(dt).forEach(a -> map.put(a.name$(), value(a, subject)));
		List<DigitalTwinOperator.Inference> infer = box.dtOperator().infer(dt);
		infer.forEach(i -> map.put(i.variable(), i.value()));
		return map;
	}

	private String value(Variable a, SubjectHistory subject) {
		if (a.isEnumerated()) return subject.current().text(a.name$());
		return subject.current().text(a.name$());
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