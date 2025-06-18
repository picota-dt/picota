package io.picota.digitaltwin.control.commands.evaluatevariablescommand;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import io.picota.digitaltwin.control.commands.DataPreparer;
import io.picota.digitaltwin.control.commands.trainvariablescommand.OneHotEncoder;
import io.picota.digitaltwin.control.commands.trainvariablescommand.TemporalColumns;
import io.picota.digitaltwin.model.Archetype;
import io.quassar.picota.DigitalTwin;
import io.quassar.picota.DigitalTwin.DigitalSubject;
import io.quassar.picota.DigitalTwin.DigitalSubject.InferenceModel;
import io.quassar.picota.Variable;
import systems.intino.datamarts.subjectstore.SubjectHistory;
import systems.intino.datamarts.subjectstore.SubjectHistory.Transaction;
import systems.intino.datamarts.subjectstore.SubjectHistoryVault;
import systems.intino.datamarts.subjectstore.SubjectHistoryView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static io.picota.digitaltwin.control.utils.Utils.variableTypes;

public class InferenceDataPreparer extends DataPreparer {
	private final Archetype archetype;
	private final Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().create();

	public InferenceDataPreparer(Archetype archetype) {
		super(archetype.dataDirectory());
		this.archetype = archetype;
	}

	public void prepareData(DigitalSubject ds, InferenceModel inferenceModel, Map<String, Object> record) throws IOException {
		try (SubjectHistoryVault vault = subjectVault()) {
			SubjectHistory history = vault.open(ds.subject().name$());
			Map<String, Variable> features = variableTypes(ds.subject());
			fillHistory(history, record, inferenceModel.lookback());
			Set<String> outputVariables = Set.of(outputVariables(inferenceModel));
			checkColumns(history, ds.subject().name$(), outputVariables);
			for (String outputVariable : outputVariables) {
				var timeHorizon = inferenceModel.timeHorizon();
				var outName = timeHorizon == 0 ? outputVariable : outputVariable + "+" + timeHorizon;
				File tsv = createTsv(ds.resolution(), inferenceModel, outputVariable, outputVariables, features, history);
				List<String> header = List.of(Files.lines(tsv.toPath()).findFirst().get().split("\t"));
				header = applyOneHotTransformations(tsv, header, features);
				JsonObject metadata = getMetadata(ds.subject().name$(), outName);
				transformToJsonl(tsv, outName, header, features, metadata, inferenceModel.lookback());
				tsv.delete();
			}
		}
	}

	private File createTsv(DigitalTwin.DigitalSubject.Resolution resolution, InferenceModel inferenceModel,
						   String outputVariable, Set<String> outputVariables, Map<String, Variable> features,
						   SubjectHistory history) throws IOException {
		File tsv = new File(dataDir, history.name() + "_" + outputVariable + TSV);
		TemporalAmount period = period(resolution);
		SubjectHistoryView.of(history)
				.from(history.first())
				.to(history.last().plus(1, ChronoUnit.HOURS))
				.period(period)
				.add(TemporalColumns.get())
				.add(inputVariables(history, inferenceModel, features, outputVariables))
				.export()
				.onlyCompleteRows()
				.to(new FileOutputStream(tsv));
		return tsv;
	}

	public JsonObject getMetadata(String subject, String variable) throws IOException {
		return gson.fromJson(Files.readString(archetype.metadataFile(subject, variable).toPath()), JsonObject.class);
	}

	private List<String> applyOneHotTransformations(File tsv, List<String> header, Map<String, Variable> variableTypes) throws IOException {
		if (variableTypes.values().stream().anyMatch(v -> !v.isNumeric()))
			return new OneHotEncoder(tsv, header, variableTypes).encode();
		return header;
	}

	public static String[] outputVariables(InferenceModel inferenceModel) {
		if (inferenceModel.variable().isComposite())
			return inferenceModel.variable().asComposite().componentsList().stream().flatMap(l -> l.values().stream())
					.map(l -> inferenceModel.variable().name$() + LAYER_SEPARATOR + l)
					.toArray(String[]::new);
		return new String[]{inferenceModel.variable().name$()};
	}

	private void checkColumns(SubjectHistory history, String subject, Set<String> outputVariables) {
		if (history.tags().isEmpty()) return;
		for (String variable : outputVariables)
			if (!history.tags().contains(variable))
				throw new IllegalStateException("Column " + variable + " not found in the dataset of " + subject);
	}

	private static void fillHistory(SubjectHistory history, Map<String, Object> dataset, InferenceModel.Lookback lookback) {
		SubjectHistory.Batch batch = history.batch();
		addTValues(dataset, batch);
		if (lookback != null) addLookback(dataset, lookback, batch);
		batch.terminate();
	}

	private static void addTValues(Map<String, Object> dataset, SubjectHistory.Batch batch) {
		Transaction t = batch.on(Instant.parse(dataset.get("instant").toString()), "");
		for (String key : dataset.keySet())
			if (!key.startsWith("instant") && !isLookback(key))
				t.put(key.trim(), valueOf(dataset.get(key)));
		t.terminate();
	}

	private static Number valueOf(Object value) {
		return value instanceof Number n ? n : Double.parseDouble(value.toString().trim());
	}

	private static void addLookback(Map<String, Object> dataset, InferenceModel.Lookback lookback, SubjectHistory.Batch batch) {
		IntStream.range(1, lookback.isDistance() ? 1 : lookback.asWindow().size()).forEach(l -> {
			if (dataset.containsKey("instant-" + l)) {
				Transaction tLookback = batch.on(Instant.parse(dataset.get("instant-" + l).toString()), "");
				for (String key : dataset.keySet()) {
					if (!key.startsWith("instant") && lookback(key) == l)
						tLookback.put(key.trim(), valueOf(dataset.get(key)));
				}
				tLookback.terminate();
			}
		});
	}

	private static SubjectHistoryVault subjectVault() {
		return new SubjectHistoryVault("jdbc:sqlite::memory:");
	}

	private static boolean isLookback(String key) {
		try {
			String substring = key.substring(key.length() - 2);
			return Integer.parseInt(substring) < 0;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	private static int lookback(String key) {
		try {
			String substring = key.substring(key.length() - 2);
			return Math.abs(Integer.parseInt(substring));
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}