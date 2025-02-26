package io.picota.runtime.ui.displays.templates;

import io.picota.runtime.RuntimeBox;
import io.picota.runtime.ui.DigitalTwin;
import io.picota.runtime.ui.datasources.DigitalTwinTimelineDatasource;

public class DigitalTwinTemplate extends AbstractDigitalTwinTemplate<RuntimeBox> {
	private DigitalTwin digitalTwin;

	public DigitalTwinTemplate(RuntimeBox box) {
		super(box);
	}

	public void digitalTwin(DigitalTwin value) {
		this.digitalTwin = value;
	}

	@Override
	public void refresh() {
		super.refresh();
		title.value(digitalTwin.title());
		timeline.source(new DigitalTwinTimelineDatasource(box(), session(), digitalTwin));
		timeline.refresh();
	}
}