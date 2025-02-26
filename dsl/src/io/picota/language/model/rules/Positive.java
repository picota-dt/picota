package io.picota.language.model.rules;

import io.intino.tara.language.model.rules.variable.VariableRule;

public class Positive implements VariableRule<Integer> {

	@Override
	public boolean accept(Integer value) {
//		return value > 0;
		return true;
	}
}
