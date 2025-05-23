package io.picota.digitalmodel.setup;

import io.intino.alexandria.logger.Logger;
import io.intino.itrules.Engine;
import io.intino.itrules.Frame;
import io.intino.itrules.FrameBuilder;
import io.intino.itrules.template.Template;
import io.intino.magritte.framework.Layer;
import io.picota.language.compiler.util.Tar;
import io.picota.language.model.DigitalTwin;
import io.picota.language.model.PicotaGraph;
import io.picota.language.model.Variable;
import org.apache.commons.io.FileUtils;
import systems.intino.datamarts.subjectstore.SubjectHistory;
import systems.intino.datamarts.subjectstore.SubjectHistoryVault;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static io.intino.alexandria.logger.Logger.error;
import static io.intino.itrules.formatters.StringFormatters.camelCase;
import static io.intino.itrules.formatters.StringFormatters.firstLowerCase;

public class TorchScriptsGenerationOperation {
	private final PicotaGraph model;
	private final SubjectHistoryVault subjectStore;
	private final Template template;
	private final File trainerDir;
	private final File evaluatorDir;
	private final File scriptsDir;

	public TorchScriptsGenerationOperation(File workingDir, PicotaGraph model, SubjectHistoryVault subjectStore) {
		this.model = model;
		this.subjectStore = subjectStore;
		this.template = new TorchScriptsTemplate();
		this.scriptsDir = new File(workingDir, "scripts");
		this.trainerDir = new File(scriptsDir, "trainer");
		this.evaluatorDir = new File(scriptsDir, "evaluator");
		clean();
		this.evaluatorDir.mkdirs();
		this.trainerDir.mkdirs();
	}

	public void execute() {
		try {
			checkColumns();
			createTrainerScripts();
			createEvaluatorScripts();
		} catch (Throwable e) {
			error("Error during script generation: " + e.getMessage(), e);
		}
	}

	private void checkColumns() {
		for (DigitalTwin digitalTwin : model.digitalTwinList()) {
			List<String> list = digitalTwin.inferList().stream().map(i -> i.variable().name$()).toList();
			SubjectHistory subject = subjectStore.open(digitalTwin.name$());
			if (subject.tags().isEmpty()) continue;
			for (String variable : list) {
				if (!subject.tags().contains(variable)) {
					throw new IllegalStateException("Subject " + subject + " does not contain tag " + variable);
				}
			}

		}
	}

	private void createTrainerScripts() throws IOException {
		createDigitalTwinScripts();
		createMainScript();
		extract("trainer", trainerDir);
	}

	private void createEvaluatorScripts() throws IOException {
		for (DigitalTwin dt : model.digitalTwinList()) {
			File file = new File(evaluatorDir, dt.name$() + ".py");
			FrameBuilder frame = new FrameBuilder("evaluator");
			frame.add("name", dt.name$());
			dt.inferList().forEach(i -> {
				FrameBuilder builder = frameBuilderOf(i.variable(), "inference");
				if (dt.isPredictive()) builder.add("timeHorizon", "+" + dt.asPredictive().timeHorizon());
				frame.add("variable", builder.toFrame());
			});
			Files.writeString(file.toPath(), new Engine(template).render(frame));
		}
		extract("evaluator", evaluatorDir);
	}

	private void extract(String lib, File dir) throws IOException {
		Tar.extractTarFile(this.getClass().getResourceAsStream("/scripts/" + lib + ".tar"), dir);
	}

	private void createMainScript() throws IOException {
		File main = new File(trainerDir, "main.py");
		FrameBuilder frame = new FrameBuilder("supermain");
		frame.add("dt", model.digitalTwinList().stream().map(Layer::name$).toArray(String[]::new));
		Files.writeString(main.toPath(), new Engine(template).render(frame));
	}

	private void createDigitalTwinScripts() throws IOException {
		for (var dt : model.digitalTwinList()) {
			File dtDir = new File(trainerDir, normalize(dt.name$()));
			dtDir.mkdirs();
			createPackage(dtDir);
//			if (dt.isPredictive()) renderPredictiveModels(dt, dtDir);
			renderInferences(dt, dtDir);
			renderDtMain(dt, new File(dtDir, "main.py"));
		}
	}

	private String normalize(String s) {
		return firstLowerCase().format(camelCase().format(s).toString()).toString();
	}

	private void renderInferences(DigitalTwin dt, File dir) throws IOException {
		for (DigitalTwin.Infer i : dt.inferList()) {
			createPackage(dir);
			File file = new File(dir, i.variable().name$() + ".py");
			Files.writeString(file.toPath(), new Engine(template).render(frameOf(i)));
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
//		Reality reality = dt.entity().core$().ownerAs(Reality.class);
//		Stream.concat(reality.viewPointList().stream(), dt.entity().viewPointList().stream())
//				.flatMap(vp -> vp.variableList().stream())
//				.filter(v -> infers.stream().noneMatch(i -> i.variable().equals(v)))
//				.forEach(v -> builder.add("variable", frameOf(v, "entity")));
		infers.forEach(i -> builder.add("variable", frameOf(i)));
		return builder.toFrame();
	}

	private Frame frameOf(DigitalTwin.Infer i) {
		FrameBuilder builder = frameBuilderOf(i.variable(), "inference");
		DigitalTwin dt = i.core$().ownerAs(DigitalTwin.class);
		builder.add("digitalTwin", dt.name$());
		if (dt.isPredictive()) builder.add("timeHorizon", "+" + dt.asPredictive().timeHorizon());
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