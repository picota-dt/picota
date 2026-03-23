package io.picota.backend.control.commands;

import io.picota.backend.control.ui.schemas.DigitalTwin;

import java.util.List;

@FunctionalInterface
public interface ListTwinsCommand {
	List<DigitalTwin> listTwins(String authToken, String status, String type, String q, String sort, String order);
}
