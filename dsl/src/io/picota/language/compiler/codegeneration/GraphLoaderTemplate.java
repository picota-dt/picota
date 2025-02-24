package io.picota.language.compiler.codegeneration;

import io.intino.itrules.template.Rule;
import io.intino.itrules.template.Template;

import java.util.ArrayList;
import java.util.List;

import static io.intino.itrules.template.condition.predicates.Predicates.*;
import static io.intino.itrules.template.outputs.Outputs.*;

public class GraphLoaderTemplate extends Template {

	public List<Rule> ruleSet() {
		List<Rule> rules = new ArrayList<>();
		rules.add(rule().condition(allTypes("graphloader")).output(literal("package ")).output(placeholder("package")).output(literal(";\n\nimport io.intino.alexandria.logger.Logger;\nimport io.intino.datahub.box.DataHubConfiguration;\nimport io.intino.datahub.model.*;\nimport io.intino.datahub.model.rules.Scale;\n\nimport java.io.File;\nimport java.io.IOException;\nimport java.nio.file.Files;\n\npublic class GraphLoader {\n\tpublic static NessGraph load(String[] args) {\n\t\tDataHubConfiguration configuration = new DataHubConfiguration(args);\n\t\tNessGraph ness = NessGraph.load();\n\t\tness.create().broker().port(Integer.parseInt(configuration.brokerPort())).secondaryPort(Integer.parseInt(configuration.brokerSecondaryPort()));\n\t\tDatalake datalake = ness.create().datalake(Scale.")).output(placeholder("scale")).output(literal(");\n\t\tDatamart master = ness.create(\"master\", \"master\").datamart();\n\t\tdatalake.create().seal().create().cron(\"0 0 4 1/1 * ? *\", null);\n\t\t")).output(placeholder("digitalTwin", "call").multiple("\n")).output(literal("\n\t\tloadUsers(configuration.home(), ness);\n\t\treturn ness;\n\t}\n\n\t")).output(placeholder("digitalTwin", "method").multiple("\n")).output(literal("\n\n\tprivate static void loadUsers(File workspace, NessGraph nessGraph) {\n\t\ttry {\n\t\t\tFile file = new File(workspace, \"datahub/config/users.bin\");\n\t\t\tif (!file.exists()) return;\n\t\t\tnessGraph.broker().clear().user(u -> true);\n\t\t\tString[] users = new String(Files.readAllBytes(file.toPath())).split(\"\\n\");\n\t\t\tfor (String user : users) nessGraph.broker().create().user(user.split(\"::\")[0], user.split(\"::\")[1]);\n\t\t} catch (IOException e) {\n\t\t\tLogger.error(e);\n\t\t}\n\t}\n}")));
		rules.add(rule().condition(all(allTypes("digitalTwin"), trigger("call"))).output(placeholder("name")).output(literal("(datalake, master);")));
		rules.add(rule().condition(all(allTypes("digitalTwin"), trigger("method"))).output(literal("private static void ")).output(placeholder("name")).output(literal("(Datalake datalake, Datamart master) {\n\tSensor ")).output(placeholder("name")).output(literal("_Sensor = datalake.graph().create(\"sensors\",\"")).output(placeholder("name")).output(literal("\").sensor();\n\t")).output(placeholder("name")).output(literal("_Sensor.create(\"resolution\").attribute(\"")).output(placeholder("period")).output(literal("\");\n\t")).output(placeholder("name")).output(literal("_Sensor.create(\"resolutionScale\").attribute(\"")).output(placeholder("scale")).output(literal("\");\n\t")).output(placeholder("name")).output(literal("_Sensor.create(\"inference\").attribute(\"")).output(placeholder("moment")).output(literal("\");\n\t")).output(expression().output(placeholder("lag"))).output(literal("\n\t")).output(expression().output(placeholder("timeHorizon"))).output(literal("\n\t")).output(expression().output(placeholder("timeHorizonScale"))).output(literal("\n\t")).output(expression().output(placeholder("variable").multiple("\n"))).output(literal("\n\tEntity ")).output(placeholder("name")).output(literal("_Entity = master.create(\"")).output(placeholder("name")).output(literal("\").entity();\n\tDatalake.Tank.Measurement ")).output(placeholder("name")).output(literal("_Tank = datalake.create(\"")).output(placeholder("name")).output(literal("\").tank().asMeasurement(")).output(placeholder("name")).output(literal("_Sensor, ")).output(placeholder("period")).output(literal(", Scale.")).output(placeholder("scale", "firstUpperCase")).output(literal(");\n\tmaster.create(\"")).output(placeholder("name")).output(literal("_Timeline\").timeline(")).output(placeholder("name")).output(literal("_Entity).asRaw(")).output(placeholder("name")).output(literal("_Tank);\n}")));
		rules.add(rule().condition(trigger("lag")).output(placeholder(new String[]{"container"}, "name")).output(literal("_Sensor.create(\"lag\").attribute(\"")).output(placeholder("")).output(literal("\");")));
		rules.add(rule().condition(trigger("timehorizon")).output(placeholder(new String[]{"container"}, "name")).output(literal("_Sensor.create(\"timeHorizon\").attribute(\"")).output(placeholder("")).output(literal("\");")));
		rules.add(rule().condition(trigger("timehorizonscale")).output(placeholder(new String[]{"container"}, "name")).output(literal("_Sensor.create(\"timeHorizonScale\").attribute(\"")).output(placeholder("")).output(literal("\");")));
		rules.add(rule().condition(all(allTypes("numeric"), trigger("variable"))).output(placeholder(new String[]{"container"}, "name")).output(literal("_Sensor.create(\"")).output(placeholder("name")).output(literal("\").magnitude(\"")).output(placeholder("name")).output(literal("\").create(\"type\").attribute(\"Numeric\")")).output(expression().output(placeholder("inference"))).output(literal(";")));
		rules.add(rule().condition(all(allTypes("numeric"), trigger("variable"))).output(placeholder(new String[]{"container"}, "name")).output(literal("_Sensor.create(\"")).output(placeholder("name")).output(literal("\").magnitude(\"")).output(placeholder("name")).output(literal("\").create(\"type\").attribute(\"Cyclic\")")).output(expression().output(placeholder("inference"))).output(literal(";")));
		rules.add(rule().condition(trigger("variable")).output(placeholder(new String[]{"container"}, "name")).output(literal("_Sensor.create(\"")).output(placeholder("name")).output(literal("\").magnitude(\"")).output(placeholder("name")).output(literal("\")")).output(expression().output(placeholder("inference"))).output(literal(";")));
		rules.add(rule().condition(trigger("inference")).output(literal(".core$().ownerAs(Sensor.Magnitude.class).create(\"inference\").attribute(\"")).output(placeholder("")).output(literal("\")")));
		return rules;
	}

	public String render(Object object) {
		return new io.intino.itrules.Engine(this).render(object);
	}

	public String render(Object object, java.util.Map<String, io.intino.itrules.Formatter> formatters) {
		return new io.intino.itrules.Engine(this).addAll(formatters).render(object);
	}
}