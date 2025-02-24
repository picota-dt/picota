package io.picota.language.compiler.codegeneration;

import io.intino.itrules.template.Rule;
import io.intino.itrules.template.Template;

import java.util.ArrayList;
import java.util.List;

import static io.intino.itrules.template.condition.predicates.Predicates.*;
import static io.intino.itrules.template.outputs.Outputs.*;

public class PicotaSetupTemplate extends Template {

	public List<Rule> ruleSet() {
		List<Rule> rules = new ArrayList<>();
		rules.add(rule().condition(allTypes("setup")).output(literal("package ")).output(placeholder("package")).output(literal(";\n\nimport io.intino.alexandria.logger4j.Logger;\nimport io.picota.runtime.PicotaStarter;\nimport io.picota.runtime.RuntimeBox;\nimport org.apache.log4j.Level;\n\npublic class Main {\n\tpublic static void main(String[] args) {\n\t\tRuntimeBox runtime = PicotaStarter.start(args, GraphLoader.load(args));\n\t\tRuntime.getRuntime().addShutdownHook(new Thread(runtime::stop));\n\t}\n}")));
		return rules;
	}

	public String render(Object object) {
		return new io.intino.itrules.Engine(this).render(object);
	}

	public String render(Object object, java.util.Map<String, io.intino.itrules.Formatter> formatters) {
		return new io.intino.itrules.Engine(this).addAll(formatters).render(object);
	}
}