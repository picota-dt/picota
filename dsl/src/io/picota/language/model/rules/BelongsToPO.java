package io.picota.language.model.rules;

import io.intino.tara.language.model.rules.variable.VariableRule;

public class BelongsToPO implements VariableRule<io.intino.tara.language.model.Mogram> {

	@Override
	public boolean accept(io.intino.tara.language.model.Mogram value) {
		return value.container().type().equals("PhysicalObject");
	}
}
