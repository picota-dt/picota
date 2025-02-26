package io.picota.runtime.ui;

public class DigitalTwin {
	public String title;

	// TODO con atributos que quieras que se visualicen

	public String title() {
		return title;
	}

	public DigitalTwin title(String title) {
		this.title = title;
		return this;
	}
}
