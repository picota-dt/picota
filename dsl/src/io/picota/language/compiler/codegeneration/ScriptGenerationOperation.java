package io.picota.language.compiler.codegeneration;

import io.intino.builder.CompilerConfiguration;
import io.intino.builder.OutputItem;
import io.intino.itrules.Engine;
import io.intino.itrules.Frame;
import io.intino.itrules.FrameBuilder;
import io.intino.itrules.template.Template;
import io.intino.magritte.framework.Layer;
import io.intino.tara.builder.core.errorcollection.TaraException;
import io.picota.language.compiler.util.Tar;
import io.picota.language.model.DigitalTwin;
import io.picota.language.model.PicotaGraph;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static java.util.logging.Level.SEVERE;

@SuppressWarnings("resource")
public class ScriptGenerationOperation extends Generator {
	private static final Logger LOG = Logger.getGlobal();
	private final Map<File, Boolean> sources;
	private final PicotaGraph model;
	private final Template template;
	private final Map<String, List<String>> outMap = new LinkedHashMap<>();
	private final File tempDir;
	private final File outDir;

	public ScriptGenerationOperation(CompilerConfiguration conf, Map<File, Boolean> sources, PicotaGraph model) {
		super(conf);
		this.outDir = conf.outDirectory();
		this.sources = sources;
		this.model = model;
		this.template = new ScriptTemplate();
		this.tempDir = new File(conf.getTempDirectory(), conf.module());
	}

	public List<OutputItem> call() throws TaraException {
		try {
			if (conf.isVerbose()) conf.out().println(prefix() + " Generating Script...");
			createScripts();
			extractLibs();
			Tar.createTarFile(tempDir, new File(outDir, "scripts.tar"));
			return toOutputList(outMap);
		} catch (Throwable e) {
			LOG.log(SEVERE, "Error during script generation: " + e.getMessage(), e);
			throw new TaraException(e.getMessage());
		}
	}

	private void extractLibs() throws IOException {
		Tar.extractTarFile(this.getClass().getResourceAsStream("/libs.tar"), tempDir);
	}

	private void createScripts() throws IOException {
		List<Frame> dtFrames = new ArrayList<>();
		for (var dt : model.digitalTwinList()) {
			File dir = new File(tempDir, dt.name$());
			dir.mkdirs();
			renderInterface(dt, dir);
			dtFrames.add(frameOf(dt));
		}
		File main = new File(tempDir, "main.py");
		renderMain(dtFrames.toArray(new Frame[0]), main);
	}

	private void renderInterface(DigitalTwin dt, File dir) throws IOException {
		for (DigitalTwin.Interface i : dt.interfaceList()) {
			File file = new File(dir, i.physicVariable().name$() + ".py");
			Files.writeString(file.toPath(), new Engine(template).render(frameOf(i)));
			put(sources.keySet().iterator().next().getAbsolutePath(), file.getAbsolutePath());
		}
	}

	private void renderMain(Frame[] dtFrames, File file) throws IOException {
		Frame frame = new FrameBuilder("main").add("dt", dtFrames).toFrame();
		Files.writeString(file.toPath(), new Engine(template).render(frame));
	}

	private Frame frameOf(DigitalTwin dt) {
		FrameBuilder builder = new FrameBuilder("dt").add("name", dt.name$());
		dt.interfaceList().forEach(i -> builder.add("variable", new FrameBuilder("variable").add("name", i.physicVariable().name$())));
		return builder.toFrame();
	}

	private Frame frameOf(DigitalTwin.Interface i) {
		return new FrameBuilder("digitalTwinVariable")
				.add("variable", i.causedBy().stream().map(Layer::name$).toArray())
				.add("output", i.physicVariable().name$())
				.toFrame();
	}
}