package io.picota.runtime.ui;

public class DigitalTwin {
	public String title;

	public String title() {
		return title;
	}

	public DigitalTwin title(String title) {
		this.title = title;
		return this;
	}
}
