package io.picota.digitaltwin.setup;

import io.intino.alexandria.logger.Logger;
import io.intino.itrules.Engine;
import io.intino.itrules.Frame;
import io.intino.itrules.FrameBuilder;
import io.intino.itrules.template.Template;
import io.intino.magritte.framework.Layer;
import io.picota.digitaltwin.utils.Compression;
import io.quassar.DigitalTwin.DigitalSubject;
import io.quassar.DigitalTwin.DigitalSubject.InferenceModel;
import io.quassar.PicotaGraph;
import io.quassar.Variable;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static io.intino.alexandria.logger.Logger.error;
import static io.intino.itrules.formatters.StringFormatters.camelCase;
import static io.intino.itrules.formatters.StringFormatters.firstLowerCase;

public class RuntimeCodeGenerator {
	private final PicotaGraph graph;
	private final Template template;
	private final File trainerDir;
	private final File evaluatorDir;
	private final File scriptsDir;

	public RuntimeCodeGenerator(File workingDir, PicotaGraph graph) {
		this.graph = graph;
		this.template = new TorchScriptsTemplate();
		this.scriptsDir = new File(workingDir, "scripts");
		this.trainerDir = new File(scriptsDir, "trainer");
		this.evaluatorDir = new File(scriptsDir, "evaluator");
		clean();
		this.evaluatorDir.mkdirs();
		this.trainerDir.mkdirs();
	}

	public void generate() {
		try {
			createTrainerScripts();
			createEvaluatorScripts();
		} catch (Throwable e) {
			error("Error during script generation: " + e.getMessage(), e);
		}
	}

	private void createTrainerScripts() throws IOException {
		createDigitalSubjectScripts();
		createMainScript();
		extract("trainer", trainerDir);
	}

	private void createEvaluatorScripts() throws IOException {
		for (DigitalSubject dt : graph.digitalTwin().digitalSubjectList()) {
			File file = new File(evaluatorDir, dt.name$() + ".py");
			FrameBuilder frame = new FrameBuilder("evaluator");
			frame.add("name", dt.name$());
			dt.inferenceModelList().forEach(i -> {
				FrameBuilder builder = frameBuilderOf(i.variable(), "inference");
				if (i.isPrediction()) builder.add("timeHorizon", "+" + i.asPrediction().timeHorizon());
				frame.add("variable", builder.toFrame());
			});
			Files.writeString(file.toPath(), new Engine(template).render(frame));
		}
		extract("evaluator", evaluatorDir);
	}

	private void extract(String lib, File dir) throws IOException {
		Compression.extractTarFile(this.getClass().getResourceAsStream("/scripts/" + lib + ".tar"), dir);
	}

	private void createMainScript() throws IOException {
		File main = new File(trainerDir, "main.py");
		FrameBuilder frame = new FrameBuilder("supermain");
		frame.add("dt", graph.digitalTwin().digitalSubjectList().stream().map(Layer::name$).toArray(String[]::new));
		Files.writeString(main.toPath(), new Engine(template).render(frame));
	}

	private void createDigitalSubjectScripts() throws IOException {
		for (DigitalSubject subject : graph.digitalTwin().digitalSubjectList()) {
			File dtDir = new File(trainerDir, normalize(subject.name$()));
			dtDir.mkdirs();
			createPackage(dtDir);
			renderInferences(subject, dtDir);
			renderSubjectMain(subject, new File(dtDir, "main.py"));
		}
	}

	private String normalize(String s) {
		return firstLowerCase().format(camelCase().format(s).toString()).toString();
	}

	private void renderInferences(DigitalSubject subject, File dir) throws IOException {
		for (InferenceModel i : subject.inferenceModelList()) {
			createPackage(dir);
			File file = new File(dir, i.variable().name$() + ".py");
			Files.writeString(file.toPath(), new Engine(template).render(frameOf(i)));
		}
	}

	private static void createPackage(File dir) throws IOException {
		File file = new File(dir, "__init__.py");
		if (!file.exists()) Files.createFile(file.toPath());
	}

	private void renderSubjectMain(DigitalSubject dt, File file) throws IOException {
		Files.writeString(file.toPath(), new Engine(template).render(frameOf(dt)));
	}

	private Frame frameOf(DigitalSubject subject) {
		FrameBuilder builder = new FrameBuilder("subject").add("name", subject.name$());
		//		Reality reality = dt.entity().core$().ownerAs(Reality.class);
//		Stream.concat(reality.viewPointList().stream(), dt.entity().viewPointList().stream())
//				.flatMap(vp -> vp.variableList().stream())
//				.filter(v -> infers.stream().noneMatch(i -> i.variable().equals(v)))
//				.forEach(v -> builder.add("variable", frameOf(v, "entity")));
		subject.inferenceModelList().forEach(i -> builder.add("variable", frameOf(i)));
		return builder.toFrame();
	}

	private Frame frameOf(InferenceModel i) {
		FrameBuilder builder = frameBuilderOf(i.variable(), "inference");
		DigitalSubject dt = i.core$().ownerAs(DigitalSubject.class);
		builder.add("subject", dt.name$());
		if (i.isPrediction()) builder.add("timeHorizon", "+" + i.asPrediction().timeHorizon());
		return builder.toFrame();
	}

	private static FrameBuilder frameBuilderOf(Variable v, String tag) {
		return new FrameBuilder(tag, "variable").add("name", v.name$());
	}

	private void clean() {
		try {
			FileUtils.deleteDirectory(this.scriptsDir);
		} catch (IOException e) {
			Logger.error(e);
		}
	}
}