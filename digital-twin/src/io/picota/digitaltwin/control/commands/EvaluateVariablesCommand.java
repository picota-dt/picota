package io.picota.digitaltwin.control.commands;

import com.google.gson.JsonObject;
import io.intino.alexandria.logger.Logger;
import io.picota.digitaltwin.DigitalTwinBox;
import io.picota.digitaltwin.control.commands.evaluatevariablescommand.InferenceDataPreparer;
import io.picota.digitaltwin.control.commands.trainvariablescommand.TrainWorkspacePreparer;
import io.picota.digitaltwin.model.Archetype;
import io.picota.digitaltwin.model.Inference;
import io.quassar.monentia.picota.DigitalTwin.DigitalSubject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.picota.digitaltwin.model.MetadataFields.OUT_MAX;
import static io.picota.digitaltwin.model.MetadataFields.OUT_MIN;

public class EvaluateVariablesCommand implements Command<List<Inference>> {
	private static final Object lock = new Object();
	private final DigitalTwinBox box;
	private final String digitalTwinId;
	private final String subject;
	private final Map<String, Object> record;
	private final File pythonVenv;
	private InferenceDataPreparer dataPreparer;

	public EvaluateVariablesCommand(DigitalTwinBox box, String digitalTwinId, String subject, Map<String, Object> record) {
		this.box = box;
		this.digitalTwinId = digitalTwinId;
		this.subject = subject;
		this.record = record;
		this.pythonVenv = new File(box.configuration().pythonVenv());
	}

	@Override
	public Result<List<Inference>> execute() {
		io.picota.digitaltwin.model.DigitalTwin digitalTwin = box.store().get(digitalTwinId);
		if (digitalTwin == null) throw new IllegalArgumentException("Digital Twin not found");
		if (digitalTwin.graph() == null)
			throw new IllegalArgumentException("Digital Twin has no description model");
		this.dataPreparer = new InferenceDataPreparer(digitalTwin.archetype());
		new TrainWorkspacePreparer(digitalTwin).generateEvaluator();
		DigitalSubject subject = digitalTwin.graph().digitalTwin().digitalSubject(s -> s.subject().name$().equals(this.subject) || this.subject.startsWith(s.subject().asPrototype().prefix()));
		if (subject == null) throw new IllegalArgumentException("Subject not found");
		return new Result<>(true, "", new ArrayList<>(infer(subject, digitalTwin.archetype())));
	}

	public List<Inference> infer(DigitalSubject ds, Archetype archetype) {
		synchronized (lock) {
			List<Inference> inferences = new ArrayList<>();
			try {
				for (DigitalSubject.InferenceModel inferenceModel : ds.inferenceModelList())
					dataPreparer.prepareData(ds, this.subject, inferenceModel, record);
				inferences.addAll(inferSubjectVariables(ds, archetype));
			} catch (IOException | InterruptedException e) {
				Logger.error(e);
			}
			return inferences;
		}
	}

	private List<Inference> inferSubjectVariables(DigitalSubject sd, Archetype archetype) throws IOException, InterruptedException {
		String pythonExecutable = pythonVenv.getAbsolutePath() + "/bin/python";
		File scriptPath = new File(archetype.evaluatorScriptsDirectory(), sd.subject().name$() + ".py");
		if (!scriptPath.exists()) throw new IOException("Main script not found: " + scriptPath.getAbsolutePath());
		Process process = new ProcessBuilder(pythonExecutable, scriptPath.getAbsolutePath(),
				archetype.trainedVariablesDirectory().getAbsolutePath(),
				archetype.dataDirectory().getAbsolutePath(),
				subject)
				.directory(archetype.evaluatorScriptsDirectory())
				.redirectErrorStream(true)
				.start();
		process.waitFor();
		String result = new String(process.getInputStream().readAllBytes());
		if (process.exitValue() != 0) throw new IOException(result.trim());
		return result.lines()
				.map(line -> line.trim().split("\t"))
				.map(f -> denormalize(new Inference(sd, f[1], Double.parseDouble(f[2])))).toList();
	}

	private Inference denormalize(Inference i) {
		try {
			JsonObject metadata = dataPreparer.getMetadata(subject, i.variable());
			double min = metadata.get(OUT_MIN).getAsDouble();
			double max = metadata.get(OUT_MAX).getAsDouble();
			return new Inference(i.subject(), i.variable(), denormalize(i.value(), min, max));
		} catch (IOException e) {
			return i;
		}
	}

	public static double denormalize(double x, double min, double max) {
		return x * (max - min) + min;
	}

}
