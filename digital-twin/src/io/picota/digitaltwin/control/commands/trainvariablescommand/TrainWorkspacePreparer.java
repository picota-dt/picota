package io.picota.digitaltwin.control.commands.trainvariablescommand;

import io.intino.alexandria.logger.Logger;
import io.intino.itrules.Engine;
import io.intino.itrules.Frame;
import io.intino.itrules.FrameBuilder;
import io.intino.itrules.template.Template;
import io.picota.digitaltwin.control.utils.Compression;
import io.picota.digitaltwin.control.utils.Utils;
import io.picota.digitaltwin.model.Archetype;
import io.picota.digitaltwin.model.DigitalTwin;
import io.quassar.monentia.picota.DigitalTwin.DigitalSubject;
import io.quassar.monentia.picota.DigitalTwin.DigitalSubject.InferenceModel;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

import static io.intino.alexandria.logger.Logger.error;
import static io.intino.itrules.formatters.StringFormatters.camelCase;
import static io.intino.itrules.formatters.StringFormatters.firstLowerCase;
import static io.picota.digitaltwin.control.utils.Utils.lookbackSize;
import static org.apache.commons.io.FilenameUtils.removeExtension;

public class TrainWorkspacePreparer {
	private final Template template;
	private final File evaluatorScriptDir;
	private final File trainScriptDir;
	private final DigitalTwin digitalTwin;
	private final TrainDataPreparer trainDataPreparer;
	private Map<DigitalSubject, List<String>> subjectTargets;
	private long models = 0;

	public TrainWorkspacePreparer(DigitalTwin digitalTwin, int minRecords) {
		this.digitalTwin = digitalTwin;
		this.template = new TorchScriptsTemplate();
		this.evaluatorScriptDir = digitalTwin.archetype().evaluatorScriptsDirectory();
		this.trainScriptDir = digitalTwin.archetype().trainerScriptsDirectory();
		this.trainDataPreparer = new TrainDataPreparer(digitalTwin, minRecords);
	}

	public long models() {
		return models;
	}

	public Map<DigitalSubject, List<String>> generateTrainer() throws Throwable {
		try {
			loadSubjectTargetsForTrain();
			createTrainerScripts();
			return subjectTargets;
		} catch (IllegalArgumentException e) {
			throw e;
		} catch (Throwable e) {
			Logger.error("Error during script generation: " + e.getMessage(), e);
			throw e;
		}
	}

	public void generateEvaluator() {
		try {
			loadSubjectTargetsForEvaluator();
			createEvaluatorScripts();
		} catch (Throwable e) {
			error("Error during script generation: " + e.getMessage(), e);
		}
	}

	private void loadSubjectTargetsForTrain() throws IOException {
		this.subjectTargets = new HashMap<>();
		for (DigitalSubject subject : digitalTwin.graph().digitalTwin().digitalSubjectList()) {
			subjectTargets.put(subject, prepareDigitalSubject(digitalTwin, subject));
		}
	}

	private void loadSubjectTargetsForEvaluator() {
		this.subjectTargets = new HashMap<>();
		for (DigitalSubject subject : digitalTwin.graph().digitalTwin().digitalSubjectList()) {
			subjectTargets.put(subject, Collections.singletonList(subject.subject().name$()));
		}
	}

	private List<String> prepareDigitalSubject(DigitalTwin digitalTwin, DigitalSubject subject) throws IOException {
		List<File> files = findFiles(digitalTwin.archetype(), subject);
		List<IOException> exceptions = new ArrayList<>();
		files.forEach(subjectDataset -> {
			try {
				digitalTwin.progressMessage("Processing " + subjectDataset.getName() + "...");
				HashMap<InferenceModel, Integer> inferences = subjectSources(subject, subjectDataset);
				models += inferences.values().stream().mapToInt(i -> i).sum();
			} catch (IOException e) {
				exceptions.add(e);
			}
		});
		if (!exceptions.isEmpty()) throw exceptions.getFirst();
		return files.stream().map(f -> removeExtension(f.getName())).toList();
	}

	private List<File> findFiles(Archetype archetype, DigitalSubject ds) {
		File rawDataDir = archetype.rawDataDirectory();
		if (ds.subject().isPrototype())
			return Utils.getFilesWithPrefix(rawDataDir, ds.subject().asPrototype().prefix());
		else {
			File file = new File(rawDataDir, ds.subject().name$() + ".csv");
			return Collections.singletonList(file.exists() ? file : new File(rawDataDir, ds.subject().name$() + ".tsv"));
		}
	}

	private HashMap<InferenceModel, Integer> subjectSources(DigitalSubject subject, File subjectDataset) throws IOException {
		HashMap<InferenceModel, Integer> map = new HashMap<>();
		for (DigitalSubject.InferenceModel inferenceModel : subject.inferenceModelList())
			if (!subjectDataset.exists() || subjectDataset.length() == 0)
				throw new IllegalArgumentException("Expected dataset " + subjectDataset.getName() + ", but it does not exist or is empty.");
			else map.put(inferenceModel, trainDataPreparer.prepareData(subject, inferenceModel, subjectDataset).size());
		return map;
	}

	private void createTrainerScripts() throws IOException {
		createDigitalSubjectScripts();
		createMainScript();
		extract("trainer", trainScriptDir);
	}

	private void createEvaluatorScripts() throws IOException {
		for (DigitalSubject subject : digitalTwin.graph().digitalTwin().digitalSubjectList()) {
			File file = new File(evaluatorScriptDir, subject.subject().name$() + ".py");
			FrameBuilder frame = new FrameBuilder("evaluator");
			frame.add("name", subject.subject().name$());
			subject.inferenceModelList().forEach(i -> {
				if (i.variable().isComposite())
					Utils.variableNamesOf(i.variable()).forEach(v -> addInferenceVariable(subject, v, i, frame));
				else addInferenceVariable(subject, i.variable().name$(), i, frame);
			});
			Files.writeString(file.toPath(), engine().render(frame));
		}
		extract("evaluator", evaluatorScriptDir);
	}

	private void addInferenceVariable(DigitalSubject subject, String variable, InferenceModel i, FrameBuilder frame) {
		FrameBuilder builder = frameBuilderOf(subject, variable, "inference");
		if (i.timeHorizon() > 0) builder.add("timeHorizon", "+" + i.timeHorizon());
		frame.add("variable", builder.toFrame());
	}

	private void extract(String lib, File dir) throws IOException {
		Compression.extractTarFile(this.getClass().getResourceAsStream("/scripts/" + lib + ".tar"), dir);
	}

	private void createMainScript() throws IOException {
		File main = new File(trainScriptDir, "main.py");
		FrameBuilder frame = new FrameBuilder("supermain");
		frame.add("subject", digitalTwin.graph().digitalTwin().digitalSubjectList().stream().map(ds -> ds.subject().name$()).toArray(String[]::new));
		Files.writeString(main.toPath(), engine().render(frame));
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

	private String normalize(String name) {
		return firstLowerCase().format(camelCase().format(name).toString()).toString().replace(":", "_");
	}

	private void renderInferences(DigitalSubject subject, File dir) throws IOException {
		for (InferenceModel i : subject.inferenceModelList()) {
			createPackage(dir);
			if (i.variable().isComposite()) Utils.variableNamesOf(i.variable()).forEach(v -> writeVariable(dir, i, v));
			else writeVariable(dir, i, i.variable().name$());
		}
	}

	private void writeVariable(File dir, InferenceModel i, String variable) {
		try {
			File file = new File(dir, normalize(variable) + ".py");
			Files.writeString(file.toPath(), engine().render(frameOf(i, variable)));
		} catch (IOException e) {
			Logger.error(e);
		}
	}

	private static void createPackage(File dir) throws IOException {
		File file = new File(dir, "__init__.py");
		if (!file.exists()) Files.createFile(file.toPath());
	}

	private void renderSubjectMain(DigitalSubject dt, File file) throws IOException {
		Files.writeString(file.toPath(), engine().render(frameOf(dt)));
	}

	private Frame frameOf(DigitalSubject ds) {
		FrameBuilder builder = new FrameBuilder("subject").add("name", ds.subject().name$());
		ds.inferenceModelList().forEach(i -> {
			if (i.variable().isComposite())
				Utils.variableNamesOf(i.variable()).forEach(v -> builder.add("variable", frameOf(i, v)));
			else builder.add("variable", frameOf(i, i.variable().name$()));
		});
		return builder.toFrame();
	}

	private Frame frameOf(InferenceModel i, String variable) {
		FrameBuilder builder = frameBuilderOf(i.core$().ownerAs(DigitalSubject.class), variable, "inference");
		DigitalSubject ds = i.core$().ownerAs(DigitalSubject.class);
		builder.add("subject", ds.subject().name$());
		builder.add("lookback", i.lookback() != null ? lookbackSize(i) : 0);
		if (i.timeHorizon() > 0) builder.add("timeHorizon", "+" + i.timeHorizon());
		return builder.toFrame();
	}

	private FrameBuilder frameBuilderOf(DigitalSubject ds, String variable, String tag) {
		return new FrameBuilder(tag, "variable")
				.add("subjects", subjectTargets.get(ds).toArray(new String[0]))
				.add("name", variable);
	}

	private Engine engine() {
		return new Engine(template).add("normalize", n -> normalize(n.toString()));
	}
}