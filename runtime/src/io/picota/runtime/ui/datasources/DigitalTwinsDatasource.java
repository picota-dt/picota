package io.picota.runtime.ui.datasources;

import io.intino.alexandria.ui.model.datasource.Filter;
import io.intino.alexandria.ui.model.datasource.Group;
import io.intino.alexandria.ui.model.datasource.PageDatasource;
import io.intino.alexandria.ui.services.push.UISession;
import io.picota.runtime.RuntimeBox;
import io.picota.runtime.ui.DigitalTwin;

import java.util.Collections;
import java.util.List;

public class DigitalTwinsDatasource extends PageDatasource<DigitalTwin> {
	private final RuntimeBox box;
	private final UISession session;

	public DigitalTwinsDatasource(RuntimeBox box, UISession session) {
		this.box = box;
		this.session = session;
	}

	@Override
	public List<DigitalTwin> items(int start, int count, String condition, List<Filter> filters, List<String> sortings) {
		List<DigitalTwin> result = load(condition);
		int from = Math.min(start, result.size());
		int end = Math.min(start + count, result.size());
		return result.subList(from, end);
	}

	@Override
	public long itemCount(String condition, List<Filter> filters) {
		return load(condition).size();
	}

	@Override
	public List<Group> groups(String key) {
		return Collections.emptyList();
	}

	private List<DigitalTwin> load(String condition) {
		return box.datahub().graph().sensorList().stream().map(s -> new DigitalTwin().title(s.name$())).toList();
	}

	private DigitalTwin sensor(int index) {
		return new DigitalTwin().title("Gemelo " + index);
	}
}
