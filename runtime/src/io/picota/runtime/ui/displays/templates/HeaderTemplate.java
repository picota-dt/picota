package io.picota.runtime.ui.displays.templates;

import io.picota.runtime.RuntimeBox;

public class HeaderTemplate extends AbstractHeaderTemplate<RuntimeBox> {

	public HeaderTemplate(RuntimeBox box) {
		super(box);
	}

	@Override
	public void refresh() {
		super.refresh();
		title.value("Picota");
	}
}