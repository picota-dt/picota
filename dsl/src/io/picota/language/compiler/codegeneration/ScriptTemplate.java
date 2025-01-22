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
		rules.add(rule().condition(allTypes("digitalTwinVariable")).output(literal("import sys\nimport torch\nfrom torch import nn\nfrom torch.utils.data import DataLoader\n\nfrom DatasetLoader import DatasetLoader\nfrom InputDataset import InputDataset\nfrom kan.KanTrainer import KanTrainer\n\nsource = sys.argv[1]\nmodel_path = sys.argv[2]\noutput_variable = \"")).output(placeholder("output")).output(literal("\"\ndevice = \"cpu\"\nbatch_size = 2\nexperiments = 20\nepochs = 5\nlr = 0.001\nloss_fn = nn.MSELoss()\nvalidation_loss_fn = nn.L1Loss()\ntest_proportion = 0.2\nvalidation_proportion = 0.16\ndt = DatasetLoader(source, \",\").load()\ndataset = InputDataset(dt, output_variable)\ndataloader = DataLoader(dataset, batch_size=batch_size, shuffle=True)\nmodel = (KanTrainer(dt.keys(), experiments, batch_size, epochs, device, test_proportion, validation_proportion, lr, loss_fn, validation_loss_fn)\n            .train(dataset))\ntorch.save(model.state_dict(), model_path)")));
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