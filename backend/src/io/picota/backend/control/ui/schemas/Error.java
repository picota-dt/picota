package io.picota.backend.control.ui.schemas;

import java.util.Map;

public record Error(
		String code,
		String message,
		Map<String, Object> details
) {
	public Error {
		details = details == null ? Map.of() : Map.copyOf(details);
	}
}
