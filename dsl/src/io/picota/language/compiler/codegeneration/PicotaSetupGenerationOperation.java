package io.picota.language.compiler.codegeneration;

import io.intino.builder.OutputItem;
import io.intino.itrules.Frame;
import io.intino.itrules.FrameBuilder;
import io.intino.tara.builder.core.CompilerConfiguration;
import io.intino.tara.builder.core.errorcollection.TaraException;
import io.picota.language.model.Environment;
import io.picota.language.model.PicotaGraph;
import io.picota.language.model.Variable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
		this.outFolder = conf.getOutDirectory();
		this.srcFolder = conf.sourceDirectories().isEmpty() ? null : conf.sourceDirectories().stream().filter(d -> !d.getName().equals("gen")).findFirst().orElse(conf.sourceDirectories().get(0));
		this.sources = sources;
		this.model = model;
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
		String output = new PicotaSetupTemplate().render(new FrameBuilder("setup").add("package", conf.workingPackage()));
		File dir = new File(srcFolder, conf.workingPackage().replace(".", File.separator));
		dir.mkdirs();
		Files.writeString(new File(dir, "Main.java").toPath(), output);
	}

	private void graphLoader() throws IOException {
		Frame[] enviroments = model.environmentList().stream().map(this::frameOf).toArray(Frame[]::new);
		Frame frame = new FrameBuilder("graphloader").add("package", conf.workingPackage()).add("scale", "Hour").add("environment", enviroments).toFrame();
		String content = new GraphLoaderTemplate().render(frame);
		File dir = new File(outFolder, conf.workingPackage().replace(".", File.separator));
		dir.mkdirs();
		Files.writeString(new File(dir, "GraphLoader.java").toPath(), content);
	}

	private Frame frameOf(Environment e) {
		FrameBuilder builder = new FrameBuilder("environment").add("name", e.name$());
		e.sensorList().forEach(s -> builder.add("sensor", frameOf(s)));
		return builder.toFrame();
	}

	private Frame frameOf(Environment.Sensor s) {
		FrameBuilder builder = new FrameBuilder("sensor").add("name", s.name$());
		builder.add("period", s.period()).add("scale", s.periodScale());
		s.variableList().forEach(variable -> builder.add("variable", frameOf(variable)));
		return builder.toFrame();
	}

	private Frame frameOf(Variable v) {
		FrameBuilder builder = new FrameBuilder("variable").add("name", v.name$());
		v.attributeList().forEach(a -> builder.add("attribute", frameOf(a)));
		return builder.toFrame();
	}

	private static FrameBuilder frameOf(Variable.Attribute a) {
		return new FrameBuilder("attribute")
				.add("name", a.name$())
				.add("value", a.value());
	}
}