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
		rules.add(rule().condition(allTypes("setup")).output(literal("package ")).output(placeholder("package")).output(literal(";\n\nimport io.intino.alexandria.logger4j.Logger;\nimport io.intino.datahub.box.DataHubBox;\nimport io.intino.datahub.box.DataHubConfiguration;\nimport io.picota.runtime.DigitalTwinBuilder;\nimport org.apache.log4j.Level;\n\nimport java.io.File;\n\npublic class Main {\n\tpublic static void main(String[] args) {\n\t\tDataHubConfiguration configuration = new DataHubConfiguration(args);\n\t\tDataHubBox box = (DataHubBox) new DataHubBox(args).put(GraphLoader.load(configuration).core$());\n\t\tLogger.setLevel(Level.ERROR);\n\t\tbox.start();\n\t\tnew DigitalTwinBuilder(box, new File(configuration.home(), \"digital-twins\"), new File(configuration.args().get(\"venv\")), Main.class.getResourceAsStream(\"/scripts.tar\")).build();\n\t\tRuntime.getRuntime().addShutdownHook(new Thread(box::stop));\n\t}\n}")));
		return rules;
	}

	public String render(Object object) {
		return new io.intino.itrules.Engine(this).render(object);
	}

	public String render(Object object, java.util.Map<String, io.intino.itrules.Formatter> formatters) {
		return new io.intino.itrules.Engine(this).addAll(formatters).render(object);
	}
}