package io.picota.language.model.rules;

import io.intino.tara.language.model.Mogram;
import io.intino.tara.language.model.rules.MogramRule;

public class Named implements MogramRule {

	public boolean accept(Mogram node) {
		return !node.isAnonymous();
	}
}
