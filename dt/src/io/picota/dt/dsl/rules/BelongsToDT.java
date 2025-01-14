package io.picota.dt.dsl.rules;

import io.intino.tara.language.model.rules.variable.VariableRule;

public class BelongsToDT implements VariableRule<io.intino.tara.language.model.Mogram> {

	@Override
	public boolean accept(io.intino.tara.language.model.Mogram value) {
		return false; //TODO
	}
}
