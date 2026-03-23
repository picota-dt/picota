package io.picota.backend.model;

import java.util.List;

public record DigitalSubject(
		String id,
		String name,
		List<Variable> variables
) {
	public DigitalSubject {
		variables = variables == null ? List.of() : List.copyOf(variables);
	}
}
