package io.picota.runtime.ui.displays.templates;

import io.picota.runtime.RuntimeBox;

import java.util.Set;

public class HeaderTemplate extends AbstractHeaderTemplate<RuntimeBox> {

	public HeaderTemplate(RuntimeBox box) {
		super(box);
	}

	@Override
	public void refresh() {
		super.refresh();
		title.value("Picota");
		state.formats(Set.of("stateStyle", "state" + box().state().name() + "Style"));
		stateMessage.value(translate(box().state().name()));
	}
}