package io.picota.runtime.ui.displays.templates;

import io.intino.alexandria.ui.displays.events.AddCollectionItemEvent;
import io.picota.runtime.RuntimeBox;
import io.picota.runtime.ui.DigitalTwin;
import io.picota.runtime.ui.datasources.DigitalTwinsDatasource;
import io.picota.runtime.ui.displays.items.DigitalTwinItem;

public class DigitalTwinsCatalog extends AbstractDigitalTwinsCatalog<RuntimeBox> {
	private DigitalTwin selected;

	public DigitalTwinsCatalog(RuntimeBox box) {
		super(box);
	}

	@Override
	public void init() {
		super.init();
		digitalTwinsList.source(new DigitalTwinsDatasource(box(), session()));
		digitalTwinsList.onAddItem(this::refreshItem);
	}

	@Override
	public void refresh() {
		super.refresh();
		digitalTwinsList.reload();
		refreshCurrent();
	}

	private void refreshCurrent() {
		noDigitalTwinSelectedMessage.visible(selected == null);
		currentDigitalTwin.visible(selected != null);
		if (!currentDigitalTwin.isVisible()) return;
		currentDigitalTwin.digitalTwin(selected);
		currentDigitalTwin.refresh();
	}

	private void refreshItem(AddCollectionItemEvent event) {
		DigitalTwin digitalTwin = event.item();
		DigitalTwinItem display = event.component();
		display.titleLink.title(digitalTwin.title());
		display.titleLink.onExecute(e -> open(digitalTwin));
	}

	private void open(DigitalTwin digitalTwin) {
		selected = digitalTwin;
		refreshCurrent();
	}

}