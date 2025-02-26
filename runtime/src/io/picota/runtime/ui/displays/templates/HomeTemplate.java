package io.picota.runtime.ui.displays.templates;

import io.intino.alexandria.ui.displays.Component;
import io.intino.alexandria.ui.displays.components.BlockConditional;
import io.picota.runtime.RuntimeBox;

import java.util.Optional;

public class HomeTemplate extends AbstractHomeTemplate<RuntimeBox> {
	private View currentView;

	public HomeTemplate(RuntimeBox box) {
		super(box);
	}

	@Override
	public void init() {
		super.init();
		refresh();
	}

	@Override
	public void refresh() {
		super.refresh();
		header.refresh();
	}

	public enum View { DigitalTwins }

	public void openHome() {
		openDigitalTwins();
	}

	private void openDigitalTwins() {
		openView(View.DigitalTwins);
		if (digitalTwinsBlock.isVisible()) digitalTwinsBlock.refresh();
	}

	private boolean openView(View view) {
		if (view == null) return false;
		if (view == currentView) return false;
		hideBlocks(view);
		blockOf(view).ifPresent(Component::show);
		currentView = view;
		return true;
	}

	private void hideBlocks(View view) {
		if (view == null) return;
		if (digitalTwinsBlock.isVisible()) digitalTwinsBlock.hide();
	}

	private Optional<BlockConditional<?, ?>> blockOf(View view) {
		BlockConditional<?, ?> block = null;
		if (view == View.DigitalTwins) block = digitalTwinsBlock;
		return Optional.ofNullable(block);
	}

}