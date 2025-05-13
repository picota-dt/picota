package io.picota.language.compiler.codegeneration;

import io.intino.builder.CompilerConfiguration;
import io.intino.builder.OutputItem;
import io.intino.itrules.Frame;
import io.intino.itrules.FrameBuilder;
import io.intino.magritte.framework.Layer;
import io.intino.tara.builder.core.errorcollection.TaraException;
import io.picota.language.model.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static java.util.logging.Level.SEVERE;

@SuppressWarnings("resource")
public class PicotaSetupGenerationOperation extends Generator {
	private static final Logger LOG = Logger.getGlobal();
	private final File outFolder;
	private final File srcFolder;
	private final Map<File, Boolean> sources;
	private final PicotaGraph model;

	public PicotaSetupGenerationOperation(CompilerConfiguration conf, Map<File, Boolean> sources, PicotaGraph model) {
		super(conf);
		this.outFolder = conf.genDirectory();
		this.srcFolder = conf.srcDirectory();
		this.sources = sources;
		this.model = model;
		sources.keySet().forEach(k -> outMap.put(k.getAbsolutePath(), new ArrayList<>()));
	}

	public List<OutputItem> call() throws TaraException {
		try {
			if (conf.isVerbose()) conf.out().println(prefix() + " Generating Digital Twin Infrastructure...");
			graphLoader();
			main();
			return toOutputList(outMap);
		} catch (Throwable e) {
			LOG.log(SEVERE, "Error during generation: " + e.getMessage(), e);
			throw new TaraException(e.getMessage());
		}
	}

	private void main() throws IOException {
		File dir = new File(srcFolder, conf.generationPackage().replace(".", File.separator));
		File file = new File(dir, "Main.java");
		if (file.exists()) return;
		dir.mkdirs();
		String output = new PicotaSetupTemplate().render(new FrameBuilder("setup").add("package", conf.generationPackage()));
		Files.writeString(file.toPath(), output);
	}

	private void graphLoader() throws IOException {
		Frame[] digitalTwins = model.digitalTwinList().stream().map(this::frameOf).toArray(Frame[]::new);
		Frame frame = new FrameBuilder("graphloader")
				.add("package", conf.generationPackage())
				.add("scale", "Hour")
				.add("digitalTwin", digitalTwins).toFrame();
		String content = new GraphLoaderTemplate().render(frame);
		File dir = new File(outFolder, conf.generationPackage().replace(".", File.separator));
		dir.mkdirs();
		File target = new File(dir, "GraphLoader.java");
		Files.writeString(target.toPath(), content);
		sources.keySet().forEach(file -> outMap.get(file.getAbsolutePath()).add(target.getAbsolutePath()));

	}

	private Frame frameOf(DigitalTwin digitalTwin) {
		FrameBuilder builder = new FrameBuilder("digitalTwin").add("name", digitalTwin.name$());
		if (digitalTwin.subject() != null) addVariableFrames(digitalTwin, builder);
		builder.add("period", digitalTwin.resolution().value()).add("scale", digitalTwin.resolution().scale().name());
		builder.add("moment", digitalTwin.isEstimate() ? "Current" : "Future");
		if (digitalTwin.isPredictive()) {
			builder.add("timeHorizon", digitalTwin.asPredictive().timeHorizon());
			builder.add("lag", digitalTwin.asPredictive().lag());
		}
		return builder.toFrame();
	}

	private void addVariableFrames(DigitalTwin digitalTwin, FrameBuilder builder) {
		Reality reality = digitalTwin.subject().core$().ownerAs(Reality.class);
		List<Frame> variables = new ArrayList<>(reality.variableList().stream().map(v -> frameOf(v, digitalTwin.inferList())).toList());
		List<Variable> subjectVars = digitalTwin.subject().variableList();
		variables.addAll(subjectVars.stream().map(v -> frameOf(v, digitalTwin.inferList())).toList());
		builder.add("variable", variables.toArray(new Frame[0]));
	}

	private Frame frameOf(Variable v, List<DigitalTwin.Infer> infers) {
		FrameBuilder builder = new FrameBuilder("variable").add("name", v.name$());
		if (v.isCyclic()) builder.add("cyclic");
		if (v.isNumeric()) builder.add("numeric");
		if (isInferenceVariable(v, infers))
			builder.add("inference", infers.getFirst().core$().ownerAs(DigitalTwin.class).isEstimate() ? "Current" : "Future");
		if (v.isLayered()) {
			builder.add("layers", v.asLayered().layerList().stream().map(Layer::name$).toArray(String[]::new));
			if (v.asLayered().aggregated()) builder.add("aggregated");
		}
		return builder.toFrame();
	}

	private static boolean isInferenceVariable(Variable v, List<DigitalTwin.Infer> infers) {
		return infers.stream().anyMatch(i -> i.variable().equals(v));
	}
}