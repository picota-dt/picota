package io.picota.runtime.ui.pages;

import io.picota.runtime.ui.displays.templates.HomeTemplate;

public class HomePage extends AbstractHomePage {

	public io.intino.alexandria.ui.Soul prepareSoul(io.intino.alexandria.ui.services.push.UIClient client) {
		return new io.intino.alexandria.ui.Soul(session) {
			@Override
			public void personify() {
				HomeTemplate component = new HomeTemplate(box);
				register(component);
				component.init();
			}
		};
	}

	@Override
	protected String title() {
		return "Picota";
	}
}