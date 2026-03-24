package io.picota.backend.control.ui.schemas;

import java.util.List;

public record DigitalSubject(
		String id,
		String name,
		TimeBucket timeBucket,
		List<Variable> variables
) {
	public DigitalSubject {
		variables = variables == null ? List.of() : List.copyOf(variables);
	}

	public DigitalSubject(String id, String name, List<Variable> variables) {
		this(id, name, null, variables);
	}
}
