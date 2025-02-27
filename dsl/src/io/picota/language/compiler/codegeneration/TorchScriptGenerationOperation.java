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
import io.picota.language.model.*;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static io.intino.itrules.formatters.StringFormatters.camelCase;
import static io.intino.itrules.formatters.StringFormatters.firstLowerCase;
import static java.util.logging.Level.SEVERE;

@SuppressWarnings("resource")
public class TorchScriptGenerationOperation extends Generator {
	private static final Logger LOG = Logger.getGlobal();
	private final Map<File, Boolean> sources;
	private final PicotaGraph model;
	private final Template template;
	private final Map<String, List<String>> outMap = new LinkedHashMap<>();
	private final File tempDir;
	private final File outDir;

	public TorchScriptGenerationOperation(CompilerConfiguration conf, Map<File, Boolean> sources, PicotaGraph model) {
		super(conf);
		this.outDir = conf.outDirectory();
		this.sources = sources;
		this.model = model;
		this.template = new TorchScriptsTemplate();
		this.tempDir = new File(conf.getTempDirectory(), conf.module());
//		this.tempDir = conf.genDirectory(); //FIXME
	}

	public List<OutputItem> call() throws TaraException {
		try {
			if (conf.isVerbose()) conf.out().println(prefix() + " Generating Script...");
			createDigitalTwinScripts();
			createMainScript();
			extract("trainer");
			Tar.createTarFile(tempDir, new File(outDir, "trainer.tar"));
			FileUtils.cleanDirectory(tempDir);
			createEvaluatorScripts();
			extract("evaluator");
			Tar.createTarFile(tempDir, new File(outDir, "evaluators.tar"));
			return toOutputList(outMap);
		} catch (Throwable e) {
			LOG.log(SEVERE, "Error during script generation: " + e.getMessage(), e);
			throw new TaraException(e.getMessage());
		}
	}

	private void createEvaluatorScripts() throws IOException {
		for (DigitalTwin digitalTwin : model.digitalTwinList()) {
			File file = new File(tempDir, digitalTwin.name$() + ".py");
			FrameBuilder frame = new FrameBuilder("evaluator");
			digitalTwin.inferList().forEach(i -> frame.add("variable", frameBuilderOf(i.variable(), "inference")));
			Files.writeString(file.toPath(), new Engine(template).render(frame));
		}
	}

	private void extract(String lib) throws IOException {
		Tar.extractTarFile(this.getClass().getResourceAsStream("/" + lib + ".tar"), tempDir);
	}

	private void createMainScript() throws IOException {
		File main = new File(tempDir, "main.py");
		FrameBuilder frame = new FrameBuilder("supermain");
		frame.add("dt", model.digitalTwinList().stream().map(Layer::name$).toArray(String[]::new));
		Files.writeString(main.toPath(), new Engine(template).render(frame));
	}

	private void createDigitalTwinScripts() throws IOException {
		for (var dt : model.digitalTwinList()) {
			File dtDir = new File(tempDir, normalize(dt.name$()));
			dtDir.mkdirs();
			createPackage(dtDir);
			if (dt.isPredictive()) renderPredictiveModels(dt, dtDir);
			renderInferences(dt, dtDir);
			renderDtMain(dt, new File(dtDir, "main.py"));
		}
	}

	private String normalize(String s) {
		return firstLowerCase().format(camelCase().format(s).toString()).toString();
	}

	private void renderPredictiveModels(DigitalTwin dt, File dir) throws IOException {
		for (ViewPoint vp : dt.entity().core$().ownerAs(Reality.class).viewPointList()) renderViewPoint(dir, vp);
		for (ViewPoint vp : dt.entity().viewPointList()) renderViewPoint(dir, vp);
	}

	private void renderViewPoint(File dir, ViewPoint viewPoint) throws IOException {
		File vpDir = new File(dir, viewPoint.name$());
		vpDir.mkdirs();
		createPackage(vpDir);
		for (Variable variable : viewPoint.variableList()) {
			File file = new File(vpDir, variable.name$() + ".py");
			Files.writeString(file.toPath(), new Engine(template).render(frameOf(variable, "viewPoint")));
		}
	}

	private void renderInferences(DigitalTwin dt, File dir) throws IOException {
		for (DigitalTwin.Infer i : dt.inferList()) {
			File vpDir = new File(dir, viewPoint(i.variable()).name$());
			vpDir.mkdirs();
			createPackage(vpDir);
			File file = new File(vpDir, i.variable().name$() + ".py");
			Files.writeString(file.toPath(), new Engine(template).render(frameOf(i)));
			put(sources.keySet().iterator().next().getAbsolutePath(), file.getAbsolutePath());
		}
	}

	private static void createPackage(File dir) throws IOException {
		File file = new File(dir, "__init__.py");
		if (!file.exists()) Files.createFile(file.toPath());
	}

	private void renderDtMain(DigitalTwin dt, File file) throws IOException {
		Files.writeString(file.toPath(), new Engine(template).render(frameOf(dt)));
	}

	private Frame frameOf(DigitalTwin dt) {
		FrameBuilder builder = new FrameBuilder("digitalTwin").add("name", dt.name$());
		List<DigitalTwin.Infer> infers = dt.inferList();
		Reality reality = dt.entity().core$().ownerAs(Reality.class);
		Stream.concat(reality.viewPointList().stream(), dt.entity().viewPointList().stream())
				.flatMap(vp -> vp.variableList().stream())
				.filter(v -> infers.stream().noneMatch(i -> i.variable().equals(v)))
				.forEach(v -> builder.add("variable", frameOf(v, "entity")));
		infers.forEach(i -> builder.add("variable", frameOf(i)));
		return builder.toFrame();
	}

	private Frame frameOf(DigitalTwin.Infer i) {
		FrameBuilder builder = frameBuilderOf(i.variable(), "inference");
		DigitalTwin twin = i.core$().ownerAs(DigitalTwin.class);
		if (twin.isPredictive()) builder.add("timeHorizon", "+" + twin.asPredictive().timeHorizon());
		return builder.toFrame();
	}

	private Frame frameOf(Variable v, String tag) {
		return frameBuilderOf(v, tag)
				.toFrame();
	}

	private static FrameBuilder frameBuilderOf(Variable v, String tag) {
		return new FrameBuilder(tag, "variable")
				.add("viewPoint", viewPoint(v).name$())
				.add("name", v.name$());
	}

	private static ViewPoint viewPoint(Variable v) {
		return v.core$().ownerAs(ViewPoint.class);
	}
}