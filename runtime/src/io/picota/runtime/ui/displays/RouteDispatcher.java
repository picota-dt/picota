package io.picota.runtime.ui.displays;

import io.intino.alexandria.ui.Soul;
import io.picota.runtime.ui.displays.templates.HomeTemplate;

public class RouteDispatcher extends AbstractRouteDispatcher {
	@Override
	public void dispatchHome(Soul soul) {
		soul.currentLayer(HomeTemplate.class).openHome();
	}
}