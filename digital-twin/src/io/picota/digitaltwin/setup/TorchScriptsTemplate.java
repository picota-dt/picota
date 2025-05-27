package io.picota.digitaltwin.setup;

import io.intino.itrules.template.Rule;
import io.intino.itrules.template.Template;

import java.util.ArrayList;
import java.util.List;

import static io.intino.itrules.template.condition.predicates.Predicates.*;
import static io.intino.itrules.template.outputs.Outputs.*;

public class TorchScriptsTemplate extends Template {

	public List<Rule> ruleSet() {
		List<Rule> rules = new ArrayList<>();
		rules.add(rule().condition(allTypes("evaluator")).output(literal("import sys\nimport torch\nimport numpy as np\nimport KanEvaluator\nfrom DatasetLoader import DatasetLoader\n\ndt = DatasetLoader(sys.argv[2], \"\\t\").load()\ntensor = torch.tensor(np.array(list(dt.values())), dtype=torch.float32).squeeze(1)\n")).output(placeholder("variable", "evaluate").multiple("\n")));
		rules.add(rule().condition(allTypes("supermain")).output(literal("import sys\n")).output(placeholder("dt", "import").multiple("\n")).output(literal("\n\n")).output(placeholder("dt", "call").multiple("\n")));
		rules.add(rule().condition(trigger("import")).output(literal("import ")).output(placeholder("", "camelcase", "firstLowercase")).output(literal(".main")));
		rules.add(rule().condition(trigger("call")).output(placeholder("", "camelcase", "firstLowercase")).output(literal(".main.train(sys.argv[1], sys.argv[2] + \"/")).output(placeholder("")).output(literal("\")")));
		rules.add(rule().condition(allTypes("digitalTwin")).output(literal("import os\nfrom DatasetLoader import DatasetLoader\n")).output(placeholder("variable", "importVariable").multiple("\n")).output(literal("\n\ndef variables(dict, output):\n\treturn {\n\t\tkey: value for key, value in dict.items()\n\t\tif \"+\" not in key or key == output\n\t}\n\ndef train(source, modeldir):\n\tif not os.path.exists(source):\n\t\tprint(\"")).output(placeholder("name")).output(literal("\tNo data\")\n\t\treturn\n\tloader = DatasetLoader(source)\n\tdataset = loader.load()\n\tmeans = loader.means()\n\tstds = loader.stds()\n\t")).output(placeholder("variable", "callVariable").multiple("\n")));
		rules.add(rule().condition(trigger("quoted")).output(literal("\"")).output(placeholder("")).output(literal("\"")));
		rules.add(rule().condition(trigger("importvariable")).output(literal("import ")).output(placeholder(new String[]{"container"}, "name", "camelcase", "firstLowercase")).output(literal(".")).output(placeholder("name")));
		rules.add(rule().condition(trigger("callvariable")).output(placeholder(new String[]{"container"}, "name", "camelcase", "firstLowercase")).output(literal(".")).output(placeholder("name")).output(literal(".train(variables(dataset, \"")).output(placeholder("name")).output(placeholder("timeHorizon")).output(literal("\"), loader.means(), loader.stds(), modeldir)")));
		rules.add(rule().condition(trigger("evaluate")).output(literal("print(\"")).output(placeholder("name")).output(literal("\t{}\".format(KanEvaluator.eval(sys.argv[1] + \"/")).output(placeholder(new String[]{"container"}, "name")).output(literal("/")).output(placeholder("viewPoint")).output(literal("/")).output(placeholder("name")).output(placeholder("timeHorizon")).output(literal(".bin\", tensor)))")));
		rules.add(rule().condition(allTypes("variable")).output(literal("import torch\nimport os\nimport Device\nfrom torch import nn\nfrom InputDataset import InputDataset\nfrom kan.KanTrainer import KanTrainer\n\ndef input_variables(dict, output):\n\treturn {key: valor for key, valor in dict.items() if key != output}\n\ndef train(dataset, means, stds, modeldir):\n\tmodel_path = modeldir + \"")).output(placeholder("name")).output(placeholder("timeHorizon")).output(literal(".bin\"\n\toutput_variable = \"")).output(placeholder("name")).output(placeholder("timeHorizon")).output(literal("\"\n\tdevice = Device.get_device()\n\tbatch_size = 2\n\tepochs = 10\n\tlr = 0.001\n\tloss_fn = nn.MSELoss()\n\tvalidation_loss_fn = nn.L1Loss()\n\ttest_proportion = 0.3\n\tdataset = TimeSeriesDataset(dataset)\n\tmodel, loss, features = (\n            KanTrainer(\"")).output(placeholder("subject")).output(literal("\", input_variables(dataset, output_variable), means, stds, output_variable,\n                       batch_size, epochs, device, test_proportion, lr, loss_fn, validation_loss_fn)\n            .train(dataset))\n\ttorch.save(model.state_dict(), model_path)\n\tprint(f\"")).output(placeholder("subject")).output(literal("\\t{output_variable}\t{loss}\t\" + \"\t\".join(features.keys()))")));
		return rules;
	}

	public String render(Object object) {
		return new io.intino.itrules.Engine(this).render(object);
	}

	public String render(Object object, java.util.Map<String, io.intino.itrules.Formatter> formatters) {
		return new io.intino.itrules.Engine(this).addAll(formatters).render(object);
	}
}