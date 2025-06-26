package io.picota.digitaltwin.control.commands.trainvariablescommand;

import io.picota.digitaltwin.control.commands.DataPreparer;
import io.picota.digitaltwin.control.utils.Utils;
import io.picota.digitaltwin.model.Archetype;
import io.picota.digitaltwin.model.DigitalTwin;
import io.picota.digitaltwin.model.MetadataFields;
import io.quassar.monentia.picota.DigitalTwin.DigitalSubject;
import io.quassar.monentia.picota.DigitalTwin.DigitalSubject.InferenceModel;
import io.quassar.monentia.picota.Variable;
import org.apache.commons.io.FilenameUtils;
import systems.intino.datamarts.subjectstore.SubjectHistory;
import systems.intino.datamarts.subjectstore.SubjectHistoryVault;
import systems.intino.datamarts.subjectstore.model.signals.NumericalSignal.Summary;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import java.util.stream.IntStream;

import static io.picota.digitaltwin.control.utils.Utils.variableTypes;
import static io.picota.digitaltwin.model.MetadataFields.MEANS;
import static io.picota.digitaltwin.model.MetadataFields.STDS;
import static java.util.stream.Collectors.toMap;

public class TrainDataPreparer extends DataPreparer {
	public static final int MIN_RECORDS = 1000;
	private final File temp;
	private final DigitalTwin digitalTwin;
	private final Archetype archetype;

	public TrainDataPreparer(Archetype archetype, DigitalTwin digitalTwin) {
		super(archetype.dataDirectory());
		this.archetype = archetype;
		this.temp = archetype.tempDirectory();
		this.digitalTwin = digitalTwin;
	}

	public void prepareData(DigitalSubject ds, InferenceModel inferenceModel, File subjectDataset) throws IOException {
		String subjectName = FilenameUtils.removeExtension(subjectDataset.getName());
		Map<String, Variable> features = variableTypes(ds.subject());
		checkDataset(features, subjectDataset);
		try (SubjectHistoryVault vault = subjectVault(temp)) {
			Set<String> outputVariables = Set.of(outputVariables(inferenceModel));
			SubjectHistory history = vault.open(subjectName);
			fillHistory(history, features, subjectDataset);
			checkColumns(history, subjectName, outputVariables);
			Map<String, Double> stds = stds(history, outputVariables, features, inferenceModel.timeHorizon() > 0);
			Map<String, Double> means = means(history, outputVariables, features, inferenceModel.timeHorizon() > 0);
			for (String outputVariable : outputVariables) {
				var timeHorizon = inferenceModel.timeHorizon();
				var outName = timeHorizon == 0 ? outputVariable : outputVariable + "+" + timeHorizon;
				File tsv = createInitialTsv(ds.resolution(), inferenceModel, outputVariable, outputVariables, features, history);
				long count = linesOf(tsv);
				if (count < MIN_RECORDS)
					throw new IllegalArgumentException("“Failed to create digital twin. Not enough completed data rows. Currently:" + count + ". Minimum required: " + MIN_RECORDS);
				else digitalTwin.progressMessage("Processing " + outputVariable + " with " + count + " records");
				List<String> header = List.of(Files.lines(tsv.toPath()).findFirst().get().split("\t"));
				header = applyOneHotTransformations(tsv, header, features);
				String[] inputVariables = header.stream().filter(f -> !f.equals(outName)).toArray(String[]::new);
				HashMap<String, Object> metadata = metadata(inferenceModel, outputVariable, stds, means, inputVariables, history);
				Files.writeString(archetype.metadataFile(history.name(), outName).toPath(), gson.toJson(metadata));
				transformToJsonl(tsv, outName, header, features, metadata, inferenceModel.lookback());
				tsv.delete();
			}
		} catch (IOException e) {
			throw e;
		}
	}

	private long linesOf(File tsv) throws IOException {
		return Files.lines(tsv.toPath()).count();
	}

	private void checkDataset(Map<String, Variable> features, File dataset) throws IOException {
		String firstLine = Files.lines(dataset.toPath()).findFirst().get();
		String separator = firstLine.contains("\t") ? "\t" : ",";
		Set<String> header = Set.of(firstLine.split(separator));
		for (String f : features.keySet()) {
			if (!header.contains(f)) throw new IllegalArgumentException("Variable " + f + " does not exist in dataset");
		}
	}

	private List<String> applyOneHotTransformations(File tsv, List<String> header, Map<String, Variable> variableTypes) throws IOException {
		if (variableTypes.values().stream().anyMatch(v -> !v.isNumeric()))
			return new OneHotEncoder(tsv, header, variableTypes).encode();
		return header;
	}

	private static HashMap<String, Object> metadata(InferenceModel inferenceModel, String outputVariable,
													Map<String, Double> stds, Map<String, Double> means, String[] inputVariables,
													SubjectHistory history) {
		Summary summary = history.query().number(outputVariable).all().summary();
		HashMap<String, Object> metadata = new HashMap<>();
		metadata.put(STDS, Arrays.stream(inputVariables).filter(stds::containsKey).mapToDouble(stds::get).toArray());
		metadata.put(MEANS, Arrays.stream(inputVariables).filter(means::containsKey).mapToDouble(means::get).toArray());
		metadata.put(MetadataFields.INPUT_VARIABLES, inputVariables);
		metadata.put(MetadataFields.LOOKBACK_SIZE, Utils.lookbackSize(inferenceModel));
		metadata.put(MetadataFields.OUT_MIN, summary.min().value());
		metadata.put(MetadataFields.OUT_MAX, summary.max() != null ? summary.max().value() : 0);//FIXME
		return metadata;
	}

	private static Map<String, Double> stds(SubjectHistory history, Set<String> outVariables, Map<String, Variable> variableTypes, boolean prediction) {
		return variableTypes.keySet().stream()
				.filter(k -> history.tags().contains(k))
				.filter(o -> (prediction || !outVariables.contains(o)) && variableTypes.get(o).isNumeric())
				.collect(toMap(t -> t, t -> history.query().number(t).all().summary().sd(), (k1, k2) -> k1, LinkedHashMap::new));
	}

	private static Map<String, Double> means(SubjectHistory history, Set<String> outVariables, Map<String, Variable> variableTypes, boolean prediction) {
		return variableTypes.keySet().stream()
				.filter(k -> history.tags().contains(k))
				.filter(o -> (prediction || !outVariables.contains(o)) && variableTypes.get(o).isNumeric())
				.collect(toMap(t -> t, t -> history.query().number(t).all().summary().mean(), (k1, k2) -> k1, LinkedHashMap::new));
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
		outputVariables.stream()
				.filter(variable -> !history.tags().contains(variable))
				.forEach(variable -> {
					throw new IllegalStateException("Column " + variable + " not found in the dataset of " + subject);
				});
	}

	private static void fillHistory(SubjectHistory history, Map<String, Variable> variables, File dataset) throws IOException {
		if (!dataset.isFile()) throw new IllegalStateException("Dataset " + dataset + " is not a regular file");
		String firstLine = Files.lines(dataset.toPath()).findFirst().get();
		String separator = firstLine.contains("\t") ? "\t" : ",";
		String[] header = firstLine.split(separator);
		SubjectHistory.Batch batch = history.batch();
		Files.lines(dataset.toPath()).skip(1)
				.map(l -> l.split(separator, -1))
				.forEach(line -> {
					SubjectHistory.Transaction t = batch.on(getInstant(line[0]), "");
					IntStream.range(1, header.length)
							.filter(i -> !line[i].trim().isEmpty())
							.forEach(i -> parse(history, variables, line, header, i, t));
					t.terminate();
				});
		batch.terminate();
	}

	private static void parse(SubjectHistory history, Map<String, Variable> variables, String[] line, String[] header, int i, SubjectHistory.Transaction t) {
		String varName = header[i].trim();
		Variable type = variables.get(varName);
		if (type != null) {
			if (type.isNumeric()) t.put(varName, Double.parseDouble(line[i].trim()));
			else t.put(varName, line[i].trim());
		} else
			throw new IllegalStateException("Variable \"" + varName + "\" found in dataset \"" + history.name() + "\", but it has not been modeled.");
	}

	private static Instant getInstant(String field) {
		try {
			return Instant.parse(field);
		} catch (java.time.format.DateTimeParseException e) {
			throw new IllegalArgumentException("Could not parse " + field + ". Expected format ISO_INSTANT. The ISO instant formatter that formats or parses an instant in UTC, such as '2011-12-03T10:15:30Z'.", e);
		}
	}

	private static SubjectHistoryVault subjectVault(File workspace) {
		return new SubjectHistoryVault("jdbc:sqlite:" + new File(workspace, "subjects.ddb"));
	}
}