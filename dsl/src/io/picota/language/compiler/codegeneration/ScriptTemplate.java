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
		rules.add(rule().condition(allTypes("main")).output(literal("import sys\n")).output(placeholder("dt", "import").multiple("\n")).output(literal("\n\n")).output(placeholder("dt", "call").multiple("\n\n")));
		rules.add(rule().condition(allTypes("digitalTwinVariable")).output(literal("import torch\nfrom torch import nn\nfrom torch.utils.data import DataLoader\n\nfrom DatasetLoader import DatasetLoader\nfrom InputDataset import InputDataset\nfrom kan.KanTrainer import KanTrainer\n\ndef train(source, modeldir):\n\tmodel_path = modeldir + \"/")).output(placeholder("output")).output(literal(".bin\"\n\toutput_variable = \"")).output(placeholder("output")).output(literal("\"\n\tdevice = \"cpu\"\n\tbatch_size = 2\n\tepochs = 5\n\tlr = 0.001\n\tloss_fn = nn.MSELoss()\n\tvalidation_loss_fn = nn.L1Loss()\n\ttest_proportion = 0.2\n\tvalidation_proportion = 0.16\n\tdt = DatasetLoader(source, \",\").load()\n\tdataset = InputDataset(dt, output_variable)\n\tdataloader = DataLoader(dataset, batch_size=batch_size, shuffle=True)\n\tmodel = (KanTrainer(dt.keys(), batch_size, epochs, device, test_proportion, validation_proportion, lr, loss_fn, validation_loss_fn)\n\t\t\t\t.train(dataset))\n\ttorch.save(model.state_dict(), model_path)")));
		rules.add(rule().condition(trigger("quoted")).output(literal("\"")).output(placeholder("")).output(literal("\"")));
		rules.add(rule().condition(trigger("call")).output(placeholder("variable", "callVariable").multiple("\n")));
		rules.add(rule().condition(trigger("importvariable")).output(literal("import ")).output(placeholder(new String[]{"container"}, "name")).output(literal(".")).output(placeholder("name")));
		rules.add(rule().condition(trigger("import")).output(placeholder("variable", "importVariable").multiple("\n")));
		rules.add(rule().condition(trigger("callvariable")).output(placeholder(new String[]{"container"}, "name")).output(literal(".")).output(placeholder("name")).output(literal(".train(sys.argv[1], sys.argv[2])")));
		return rules;
	}

	public String render(Object object) {
		return new io.intino.itrules.Engine(this).render(object);
	}

	public String render(Object object, java.util.Map<String, io.intino.itrules.Formatter> formatters) {
		return new io.intino.itrules.Engine(this).addAll(formatters).render(object);
	}
}