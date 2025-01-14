package io.picota.dt.compiler.codegeneration;

import io.intino.builder.OutputItem;
import io.intino.itrules.Engine;
import io.intino.itrules.Frame;
import io.intino.itrules.FrameBuilder;
import io.intino.itrules.template.Template;
import io.intino.magritte.framework.Layer;
import io.intino.tara.builder.core.CompilerConfiguration;
import io.intino.tara.builder.core.errorcollection.TaraException;
import io.picota.dt.dsl.Input;
import io.picota.dt.dsl.PicotaGraph;
import io.picota.dt.dsl.Training;
import io.picota.dt.dsl.Training.Relation;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static io.intino.builder.BuildConstants.PRESENTABLE_MESSAGE;
import static java.util.logging.Level.SEVERE;

@SuppressWarnings("resource")
public class ScriptGenerationOperation {
	private static final Logger LOG = Logger.getGlobal();
	private final CompilerConfiguration conf;
	private final PicotaGraph model;
	private final File srcFolder;
	private final File outFolder;
	private final Template template;
	private final Map<String, List<String>> outMap = new LinkedHashMap<>();


	public ScriptGenerationOperation(CompilerConfiguration conf, PicotaGraph model) {
		this.conf = conf;
		this.outFolder = conf.getOutDirectory();
		this.srcFolder = srcDir(conf);
		this.model = model;
		this.template = new ScriptTemplate();
	}

	public List<OutputItem> call() throws TaraException {
		try {
			if (conf.isVerbose()) conf.out().println(prefix() + " Generating Script...");
			createScripts();
			return toOutputList(outMap);
		} catch (Throwable e) {
			LOG.log(SEVERE, "Error during script generation: " + e.getMessage(), e);
			throw new TaraException(e.getMessage());
		}
	}

	private void createScripts() throws IOException, URISyntaxException {
		for (Training training : model.trainingList()) {
			File dir = new File(outFolder, training.dt().name$().toLowerCase() + "_" + training.name$());
			dir.mkdirs();
			renderRelation(training, dir);
		}
	}

	private void renderRelation(Training training, File dir) throws IOException, URISyntaxException {
		for (Relation relation : training.relationList()) {
			Files.writeString(new File(dir, relation.property().name$() + ".py").toPath(), new Engine(template).render(frameOf(training, relation)));
			put(new File(training.sourceFile().toURI().getPath()).getAbsolutePath(), dir.getAbsolutePath());
		}
	}

	private Frame frameOf(Training training, Relation relation) {
		return new FrameBuilder("script")
				.add("sourceFile", training.sourceFile().getPath())
				.add("separator", training.sourceFileType().equals(Training.SourceFileType.tsv) ? "\\t" : ",")
				.add("variables", relation.causedBy().stream().map(Layer::name$).toList())
				.add("cyclic", relation.causedBy().stream().filter(Input::isCyclic).map(Layer::name$).toList())
				.add("normalized", relation.causedBy().stream().filter(Input::isNormalized).map(Layer::name$).toList())
				.toFrame();
	}

	private void put(String key, String value) {
		if (!outMap.containsKey(key)) outMap.put(key, new ArrayList<>());
		outMap.get(key).add(value);
	}

	private List<OutputItem> toOutputList(Map<String, List<String>> outMap) {
		List<OutputItem> items = new ArrayList<>();
		outMap.keySet().forEach(key -> outMap.get(key).stream().map(value -> new OutputItem(key, value)).forEach(items::add));
		return items;
	}

	private String prefix() {
		return PRESENTABLE_MESSAGE + "[" + conf.getModule() + " - " + conf.model().outDsl() + "]";
	}

	private File srcDir(CompilerConfiguration conf) {
		return conf.sourceDirectories().isEmpty() ? null : conf.sourceDirectories().stream()
				.filter(d -> !d.getName().equals("gen"))
				.findFirst()
				.orElse(this.conf.sourceDirectories().get(0));
	}
}