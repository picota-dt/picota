package io.picota.runtime.ui.displays.templates;

import io.intino.alexandria.Scale;
import io.intino.alexandria.logger.Logger;
import io.intino.alexandria.ui.displays.events.SelectEvent;
import io.intino.sumus.chronos.TimelineStore;
import io.picota.runtime.RuntimeBox;
import io.picota.runtime.ui.DigitalTwin;
import io.picota.runtime.ui.datasources.DigitalTwinDateNavigatorDatasource;
import io.picota.runtime.ui.datasources.DigitalTwinTimelineDatasource;

import java.io.IOException;

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
		var sensor = box().datahub().graph().sensorList(s -> s.name$().equalsIgnoreCase(digitalTwin.title())).findFirst().get();
		var store = timelineStore(box());
		var timelineSource = loadTimeline(store);
		navigator.source(new DigitalTwinDateNavigatorDatasource(sensor, timelineSource));
		navigator.refresh();
		timeline.summaryPointsCount(3);
		timeline.source(new DigitalTwinTimelineDatasource(sensor, timelineSource));
		timeline.refresh();
	}

	@Override
	public void init() {
		super.init();
		navigator.onSelectScale(this::onChangeScale);
	}

	private void onChangeScale(SelectEvent event) {
		Scale option = event.option();
		if (option == Scale.Year) timeline.summaryPointsCount(3);
		else timeline.summaryPointsCount(10);
	}

	private TimelineStore timelineStore(RuntimeBox box) {
		return box.datahub().datamarts().get("master").timelineStore().get(digitalTwin.title, digitalTwin.title);
	}

	private io.intino.sumus.chronos.Timeline loadTimeline(TimelineStore store) {
		try {
			return store == null ? null : store.timeline();
		} catch (IOException e) {
			Logger.error(e);
			return null;
		}
	}
}