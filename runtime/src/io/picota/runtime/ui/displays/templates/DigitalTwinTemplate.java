package io.picota.runtime.ui.displays.templates;

import io.picota.runtime.RuntimeBox;
import io.picota.runtime.ui.DigitalTwin;
import io.picota.runtime.ui.datasources.DigitalTwinDateNavigatorDatasource;
import io.picota.runtime.ui.datasources.DigitalTwinTimelineDatasource;

public class DigitalTwinTemplate extends AbstractDigitalTwinTemplate<RuntimeBox> {
	private DigitalTwin digitalTwin;

	public DigitalTwinTemplate(RuntimeBox box) {
		super(box);
	}

	public void digitalTwin(DigitalTwin value) {
		this.digitalTwin = value;
		navigator.bindTo(timeline);
	}

	@Override
	public void refresh() {
		super.refresh();
		title.value(digitalTwin.title());
		navigator.source(new DigitalTwinDateNavigatorDatasource(box(), session(), digitalTwin));
		navigator.refresh();
		timeline.source(new DigitalTwinTimelineDatasource(box(), session(), digitalTwin));
		timeline.refresh();
	}
}