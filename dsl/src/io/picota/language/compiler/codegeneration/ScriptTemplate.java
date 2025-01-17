package io.picota.language.compiler.codegeneration;

import io.intino.itrules.template.Rule;
import io.intino.itrules.template.Template;

import java.util.ArrayList;
import java.util.List;

import static io.intino.itrules.template.condition.predicates.Predicates.*;
import static io.intino.itrules.template.outputs.Outputs.*;

public class ScriptTemplate extends Template {

	public List<Rule> ruleSet() {
		List<Rule> rules = new ArrayList<>();
		rules.add(rule().condition(allTypes("variable")).output(literal("from torch import nn\nfrom torch.utils.data import DataLoader\n\nfrom DatasetLoader import DatasetLoader\nfrom InputDataset import InputDataset\nfrom kan.KanTrainer import KanTrainer\nfrom mappers.CyclicMapper import CyclicMapper\nfrom mappers.NormalizationMapper import NormalizationMapper\n\npath = \"")).output(placeholder("sourceFile")).output(literal("\"\ncol_sep = \"")).output(placeholder("separator")).output(literal("\"\nvariables = [")).output(placeholder("variables", "quoted").multiple(", ")).output(literal(", \"")).output(placeholder("output")).output(literal("\"]\ntransformedvariables = [")).output(placeholder("variables", "format").multiple(", ")).output(literal(", \"")).output(placeholder("output")).output(literal("\"]\nnormalizedVariables = [")).output(placeholder("normalized", "quoted").multiple(", ")).output(literal(", \"")).output(placeholder("output")).output(literal("\"]\noutput_variable = \"")).output(placeholder("output")).output(literal("\"\ndevice = \"cpu\"\nbatch_size = 2\nexperiments = 20\nepochs = 5\nlr = 0.001\nloss_fn = nn.MSELoss()\nvalidation_loss_fn = nn.L1Loss()\ntest_proportion = 0.2\nvalidation_proportion = 0.16\n\ndt = DatasetLoader(path, col_sep, variables).load()\ndt = NormalizationMapper(normalizedVariables).map(dt)\n")).output(placeholder("cyclicVariables", "map").multiple("\n")).output(literal("\ndataset = InputDataset(dt, output_variable)\ndataloader = DataLoader(dataset, batch_size=batch_size, shuffle=True)\n(KanTrainer(transformedvariables, experiments, batch_size, epochs, device, test_proportion, validation_proportion, lr, loss_fn, validation_loss_fn)\n            .train(dataset))")));
		rules.add(rule().condition(all(allTypes("cyclic"), trigger("map"))).output(literal("dt = CyclicMapper([\"")).output(placeholder("name")).output(literal("\"], ")).output(placeholder("cycle")).output(literal(").map(dt)")));
		rules.add(rule().condition(all(allTypes("cyclic"), trigger("format"))).output(literal("\"")).output(placeholder("name")).output(literal("_sin\", \"")).output(placeholder("name")).output(literal("_cos\"")));
		rules.add(rule().condition(all(allTypes("variable"), trigger("format"))).output(literal("\"")).output(placeholder("name")).output(literal("\"")));
		rules.add(rule().condition(all(allTypes("variable"), trigger("quoted"))).output(literal("\"")).output(placeholder("name")).output(literal("\"")));
		rules.add(rule().condition(trigger("quoted")).output(literal("\"")).output(placeholder("")).output(literal("\"")));
		return rules;
	}

	public String render(Object object) {
		return new io.intino.itrules.Engine(this).render(object);
	}

	public String render(Object object, java.util.Map<String, io.intino.itrules.Formatter> formatters) {
		return new io.intino.itrules.Engine(this).addAll(formatters).render(object);
	}
}