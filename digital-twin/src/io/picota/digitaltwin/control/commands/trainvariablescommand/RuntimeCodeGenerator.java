package io.picota.digitaltwin.control.commands.trainvariablescommand;

import io.intino.itrules.Engine;
import io.intino.itrules.Frame;
import io.intino.itrules.FrameBuilder;
import io.intino.itrules.template.Template;
import io.picota.digitaltwin.model.DigitalTwin;
import io.picota.digitaltwin.control.utils.Compression;
import io.quassar.picota.DigitalTwin.DigitalSubject;
import io.quassar.picota.DigitalTwin.DigitalSubject.InferenceModel;
import io.quassar.picota.Variable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static io.intino.alexandria.logger.Logger.error;
import static io.intino.itrules.formatters.StringFormatters.camelCase;
import static io.intino.itrules.formatters.StringFormatters.firstLowerCase;

public class RuntimeCodeGenerator {
	private final Template template;
	private final File evaluatorScriptDir;
	private final File trainScriptDir;
	private final DigitalTwin digitalTwin;
	private final Map<DigitalSubject, List<String>> subjectTargets;

	public RuntimeCodeGenerator(DigitalTwin digitalTwin, Map<DigitalSubject, List<String>> subjectTargets) {
		this.digitalTwin = digitalTwin;
		this.template = new TorchScriptsTemplate();
		this.subjectTargets = subjectTargets;
		this.evaluatorScriptDir = digitalTwin.archetype().evaluatorScriptsDirectory();
		this.trainScriptDir = digitalTwin.archetype().trainerScriptsDirectory();
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
		extract("trainer", trainScriptDir);
	}

	private void createEvaluatorScripts() throws IOException {
		for (DigitalSubject subject : digitalTwin.graph().digitalTwin().digitalSubjectList()) {
			File file = new File(evaluatorScriptDir, subject.name$() + ".py");
			FrameBuilder frame = new FrameBuilder("evaluator");
			frame.add("name", subject.name$());
			subject.inferenceModelList().forEach(i -> {
				FrameBuilder builder = frameBuilderOf(subject, i.variable(), "inference");
				if (i.isPrediction()) builder.add("timeHorizon", "+" + i.asPrediction().timeHorizon());
				frame.add("variable", builder.toFrame());
			});
			Files.writeString(file.toPath(), new Engine(template).render(frame));
		}
		extract("evaluator", evaluatorScriptDir);
	}

	private void extract(String lib, File dir) throws IOException {
		Compression.extractTarFile(this.getClass().getResourceAsStream("/scripts/" + lib + ".tar"), dir);
	}

	private void createMainScript() throws IOException {
		File main = new File(trainScriptDir, "main.py");
		FrameBuilder frame = new FrameBuilder("supermain");
		frame.add("subject", digitalTwin.graph().digitalTwin().digitalSubjectList().stream().map(ds -> ds.subject().name$()).toArray(String[]::new));
		Files.writeString(main.toPath(), new Engine(template).render(frame));
	}

	private void createDigitalSubjectScripts() throws IOException {
		for (DigitalSubject subject : digitalTwin.graph().digitalTwin().digitalSubjectList()) {
			File dtDir = new File(trainScriptDir, normalize(subject.subject().name$()));
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

	private Frame frameOf(DigitalSubject ds) {
		FrameBuilder builder = new FrameBuilder("subject").add("name", ds.subject().name$());
		//		Reality reality = dt.entity().core$().ownerAs(Reality.class);
//		Stream.concat(reality.viewPointList().stream(), dt.entity().viewPointList().stream())
//				.flatMap(vp -> vp.variableList().stream())
//				.filter(v -> infers.stream().noneMatch(i -> i.variable().equals(v)))
//				.forEach(v -> builder.add("variable", frameOf(v, "entity")));
		ds.inferenceModelList().forEach(i -> builder.add("variable", frameOf(i)));
		return builder.toFrame();
	}

	private Frame frameOf(InferenceModel i) {
		FrameBuilder builder = frameBuilderOf(i.core$().ownerAs(DigitalSubject.class), i.variable(), "inference");
		DigitalSubject ds = i.core$().ownerAs(DigitalSubject.class);
		builder.add("subject", ds.subject().name$());
		builder.add("lookback", i.asType().lookBack());
		if (i.isPrediction()) builder.add("timeHorizon", "+" + i.asPrediction().timeHorizon());
		return builder.toFrame();
	}

	private FrameBuilder frameBuilderOf(DigitalSubject ds, Variable v, String tag) {
		return new FrameBuilder(tag, "variable")
				.add("subjects", subjectTargets.get(ds).toArray(new String[0]))
				.add("name", v.name$());
	}
}