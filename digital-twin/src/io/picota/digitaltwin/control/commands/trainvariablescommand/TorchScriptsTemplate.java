package io.picota.digitaltwin.control.commands.trainvariablescommand;

import io.intino.itrules.template.Rule;
import io.intino.itrules.template.Template;

import java.util.ArrayList;
import java.util.List;

import static io.intino.itrules.template.condition.predicates.Predicates.allTypes;
import static io.intino.itrules.template.condition.predicates.Predicates.trigger;
import static io.intino.itrules.template.outputs.Outputs.literal;
import static io.intino.itrules.template.outputs.Outputs.placeholder;

public class TorchScriptsTemplate extends Template {

	public List<Rule> ruleSet() {
		List<Rule> rules = new ArrayList<>();
		rules.add(rule().condition(allTypes("evaluator")).output(literal("import sys\n\nimport kan.KanEvaluator as KanEvaluator\nfrom kan.DatasetLoader import DatasetLoader\n\n")).output(placeholder("variable", "evaluate").multiple("\n")));
		rules.add(rule().condition(allTypes("supermain")).output(literal("import sys\n")).output(placeholder("subject", "import").multiple("\n")).output(literal("\n\n")).output(placeholder("subject", "call").multiple("\n")));
		rules.add(rule().condition(trigger("import")).output(literal("import ")).output(placeholder("", "camelcase", "firstLowercase")).output(literal(".main")));
		rules.add(rule().condition(trigger("call")).output(placeholder("", "camelcase", "firstLowercase")).output(literal(".main.train(sys.argv[1], sys.argv[2])")));
		rules.add(rule().condition(allTypes("subject")).output(literal("import os\nfrom kan.DatasetLoader import DatasetLoader\n")).output(placeholder("variable", "importVariable").multiple("\n")).output(literal("\n\ndef train(source, modelsdir):\n\t")).output(placeholder("subjectName").multiple("\n")).output(literal("\n\t")).output(placeholder("variable", "callVariable").multiple("\n")));
		rules.add(rule().condition(trigger("quoted")).output(literal("\"")).output(placeholder("")).output(literal("\"")));
		rules.add(rule().condition(trigger("importvariable")).output(literal("import ")).output(placeholder(new String[]{"container"}, "name", "firstLowerCase")).output(literal(".")).output(placeholder("name", "normalize")));
		rules.add(rule().condition(trigger("callvariable")).output(literal("subjects = {")).output(placeholder("subjects", "quoted").multiple(",")).output(literal("}\nfor s in subjects:\n\tjsonl_path = os.path.join(source, s + \"_")).output(placeholder("name")).output(literal(".jsonl\")\n\tif not os.path.exists(jsonl_path):\n\t\tprint(f\"{s}\t")).output(placeholder("name")).output(literal("\tNaN\", flush=True)\n\t\tcontinue\n\tloader = DatasetLoader(jsonl_path)\n\t")).output(placeholder(new String[]{"container"}, "name", "camelcase", "firstLowercase")).output(literal(".")).output(placeholder("name", "normalize")).output(literal(".train(\n\t\ts,\n\t\tloader.load(),\n\t\tloader.get_input_variables(),\n\t\tloader.get_means(),\n\t\tloader.get_stds(),\n\t\tloader.get_out_min(),\n\t\tloader.get_out_max(),\n\t\tmodelsdir + \"/\" + s\n\t)")));
		rules.add(rule().condition(trigger("evaluate")).output(literal("loader = DatasetLoader(sys.argv[2] + \"/\" + sys.argv[3] + \"_")).output(placeholder("name")).output(literal(".jsonl\")\ndata = loader.load()\nprint(sys.argv[3] + \"\t")).output(placeholder("name")).output(placeholder("timeHorizon")).output(literal("\t{}\".format(KanEvaluator.eval(sys.argv[1] + \"/\" + sys.argv[3] + \"/")).output(placeholder("name")).output(placeholder("timeHorizon")).output(literal(".bin\",\n\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\tdata,\n\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t  \tloader.get_input_variables(),\n\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t  \tloader.get_means(),\n\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t  \tloader.get_stds(),\n\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t  \tloader.get_lookback())), flush=True)")));
		rules.add(rule().condition(allTypes("variable")).output(literal("import torch\nimport os\nfrom torch import nn\n\nimport Device\nfrom kan.TimeSeriesDataset import TimeSeriesDataset\nfrom kan.KanTrainer import KanTrainer\n\ndef input_variables(dict, output):\n\treturn {key: valor for key, valor in dict.items() if key != output}\n\ndef train(subject, datasource, input_variables, means, stds, out_min, out_max, model_dir):\n\tmodel_path = model_dir + \"/")).output(placeholder("name")).output(placeholder("timeHorizon")).output(literal(".bin\"\n\tos.makedirs(model_dir, exist_ok=True)\n\toutput_variable = \"")).output(placeholder("name")).output(placeholder("timeHorizon")).output(literal("\"\n\tdevice = Device.get_device()\n\tbatch_size = 32\n\tepochs = 50\n\tlr = 0.0001\n\tloss_fn = nn.MSELoss()\n\tvalidation_loss_fn = nn.L1Loss()\n\ttest_proportion = 0.3\n\tdataset = TimeSeriesDataset(datasource)\n\tmodel, loss, margin_of_error, features = (\n            KanTrainer(subject, input_variables, output_variable, ")).output(placeholder("lookback")).output(literal(", means, stds, out_min, out_max,\n                       batch_size, epochs, device, test_proportion, lr, loss_fn, validation_loss_fn)\n            .train(dataset))\n\ttorch.save(model.state_dict(), model_path)\n\tprint(\n        f\"{subject}\t{output_variable}\t{loss}\t{margin_of_error}\t\" +\n        \",\".join(f\"{k}###{float(v):.2f}\" for k, v in sorted(features.items(), key=lambda x: float(x[1]), reverse=True)), flush=True\n    )")));
		return rules;
	}

	public String render(Object object) {
		return new io.intino.itrules.Engine(this).render(object);
	}

	public String render(Object object, java.util.Map<String, io.intino.itrules.Formatter> formatters) {
		return new io.intino.itrules.Engine(this).addAll(formatters).render(object);
	}
}