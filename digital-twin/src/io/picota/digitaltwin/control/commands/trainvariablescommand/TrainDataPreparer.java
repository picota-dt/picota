package io.picota.digitaltwin.control.commands.trainvariablescommand;

import com.google.gson.Gson;
import io.intino.alexandria.Scale;
import io.intino.alexandria.logger.Logger;
import io.picota.digitaltwin.control.utils.Utils;
import io.picota.digitaltwin.model.Archetype;
import io.picota.digitaltwin.model.MetadataFields;
import io.quassar.picota.DigitalTwin.DigitalSubject;
import io.quassar.picota.DigitalTwin.DigitalSubject.InferenceModel;
import io.quassar.picota.DigitalTwin.DigitalSubject.InferenceModel.Lookback;
import io.quassar.picota.DigitalTwin.DigitalSubject.Resolution;
import io.quassar.picota.Variable;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.apache.commons.io.FilenameUtils;
import systems.intino.datamarts.subjectstore.SubjectHistory;
import systems.intino.datamarts.subjectstore.SubjectHistoryVault;
import systems.intino.datamarts.subjectstore.SubjectHistoryView;
import systems.intino.datamarts.subjectstore.calculator.model.filters.LeadFilter;
import systems.intino.datamarts.subjectstore.calculator.model.filters.MinMaxNormalizationFilter;
import systems.intino.datamarts.subjectstore.model.signals.NumericalSignal.Summary;
import systems.intino.datamarts.subjectstore.view.history.format.ColumnDefinition;

import java.io.*;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.picota.digitaltwin.control.utils.Utils.*;
import static io.picota.digitaltwin.model.MetadataFields.MEANS;
import static io.picota.digitaltwin.model.MetadataFields.STDS;
import static java.time.temporal.ChronoUnit.HOURS;
import static java.util.stream.Collectors.toMap;

public class TrainDataPreparer {
	public static final String TSV = ".tsv";
	public static final String JSONL = ".jsonl";
	public static final String LAYER_SEPARATOR = ":";
	public static final double[][] EMPTY = new double[0][0];
	private final File temp;
	private final Archetype archetype;
	private final File dataDir;
	private final Gson gson = new Gson();

	public TrainDataPreparer(Archetype archetype) {
		this.archetype = archetype;
		this.temp = archetype.tempDirectory();
		this.dataDir = archetype.dataDirectory();
	}

	public void prepareData(DigitalSubject ds, InferenceModel inferenceModel, File subjectDataset) throws IOException {
		String subjectName = FilenameUtils.removeExtension(subjectDataset.getName());
		Map<String, Variable> variableTypes = variableTypes(ds.subject());
		try (SubjectHistoryVault vault = subjectVault(temp)) {
			SubjectHistory history = vault.open(subjectName);
			fillHistory(history, variableTypes, subjectDataset);
			Set<String> outputVariables = Set.of(outputVariables(inferenceModel));
			Map<String, Double> stds = stds(history, outputVariables);
			Map<String, Double> means = means(history, outputVariables);
			checkColumns(history, subjectName, outputVariables);
			for (String outputVariable : outputVariables) {
				var timeHorizon = inferenceModel.timeHorizon();
				var outName = timeHorizon == 0 ? outputVariable : outputVariable + "+" + timeHorizon;
				File tsv = createInitialTsv(ds.resolution(), inferenceModel, outputVariable, outputVariables, history);
				List<String> header = List.of(Files.lines(tsv.toPath()).findFirst().get().split("\t"));
				header = applyOneHotTransformations(tsv, header, variableTypes);
				HashMap<String, Object> metadata = metadata(inferenceModel, outputVariable, stds, means, history, outputVariables, header);
				Files.writeString(archetype.metadataFile(history.name(), outName).toPath(), gson.toJson(metadata));
				transformToJsonl(tsv, outName, header, variableTypes, metadata, inferenceModel.lookback());
				tsv.delete();
			}
		} catch (IOException e) {
			throw e;
		}
	}

	private List<String> applyOneHotTransformations(File tsv, List<String> header, Map<String, Variable> variableTypes) throws IOException {
		if (variableTypes.values().stream().anyMatch(v -> !v.isNumeric())) {
			return new OneHotEncoder(tsv, header, variableTypes).encode();
		}
		return header;
	}

	private static HashMap<String, Object> metadata(InferenceModel inferenceModel, String outputVariable,
													Map<String, Double> stds, Map<String, Double> means,
													SubjectHistory history, Set<String> outputVariables, List<String> header) {
		Summary summary = history.query().number(outputVariable).all().summary();
		String[] inputVariables = header.stream().filter(f -> !outputVariables.contains(f)).toArray(String[]::new);
		HashMap<String, Object> metadata = new HashMap<>();
		metadata.put(STDS, Arrays.stream(inputVariables).filter(v -> !TemporalColumns.is(v)).mapToDouble(key -> stds.getOrDefault(key, 0.5)).toArray());//TODO qué hacer con las onehot
		metadata.put(MEANS, Arrays.stream(inputVariables).filter(v -> !TemporalColumns.is(v)).mapToDouble(key -> means.getOrDefault(key, 0.5)).toArray());
		metadata.put(MetadataFields.INPUT_VARIABLES, inputVariables);
		metadata.put(MetadataFields.LOOKBACK_SIZE, Utils.lookbackSize(inferenceModel));
		metadata.put(MetadataFields.OUT_MIN, summary.min().value());
		metadata.put(MetadataFields.OUT_MAX, summary.max() != null ? summary.max().value() : 0);//FIXME
		return metadata;
	}

	private static Map<String, Double> stds(SubjectHistory history, Set<String> outVariables) {//TODO qué hacer con las onehot
		return history.tags().stream()
				.filter(o -> !outVariables.contains(o))
				.collect(toMap(t -> t, t -> history.query().number(t).all().summary().sd()));

	}

	private static Map<String, Double> means(SubjectHistory history, Set<String> outVariables) {
		return history.tags().stream()
				.filter(o -> !outVariables.contains(o))
				.collect(toMap(t -> t, t -> history.query().number(t).all().summary().mean()));
	}

	private File createInitialTsv(Resolution resolution, InferenceModel inferenceModel, String outputVariable, Set<String> outputVariables, SubjectHistory history) throws IOException {
		File tsv = new File(dataDir, history.name() + "_" + outputVariable + TSV);
		ChronoUnit scale = chronoUnitOf(resolution.scale());
		var timeHorizon = inferenceModel.timeHorizon();
		SubjectHistoryView.of(history)
				.from(history.first().truncatedTo(scale))
				.to(history.last().plus(1, scale).truncatedTo(HOURS).minus(timeHorizon, chronoUnitOf(resolution.scale())))
				.period(period(resolution))
				.add(TemporalColumns.get())
				.add(inputVariables(history, inferenceModel, outputVariables))
				.add(outputVariable(inferenceModel, outputVariable))
				.export()
				.onlyCompleteRows()
				.to(new FileOutputStream(tsv));
		return tsv;
	}

	public static String[] outputVariables(InferenceModel inferenceModel) {
		if (inferenceModel.variable().isComposite())
			return inferenceModel.variable().asComposite().componentsList().stream().flatMap(l -> l.values().stream())
					.map(l -> inferenceModel.variable().name$() + LAYER_SEPARATOR + l)
					.toArray(String[]::new);
		return new String[]{inferenceModel.variable().name$()};
	}

	private static List<ColumnDefinition> inputVariables(SubjectHistory history, InferenceModel inferenceModel, Set<String> outputVariables) {
		int prediction = inferenceModel.timeHorizon();
		return history.tags().stream()
				.filter(t -> prediction > 0 || !outputVariables.contains(t))
				.map(t1 -> new ColumnDefinition(t1, t1 + ".first")).toList();
	}

	private File transformToJsonl(File source, String outputVariable, List<String> header, Map<String, Variable> variableTypes, Map<String, Object> metadata, Lookback lookback) throws IOException {
		File jsonl = new File(source.getParentFile(), source.getName().replace(TSV, JSONL));
		Set<String> features = header.stream().filter(f -> !f.equals(outputVariable) && !f.contains("_sin") && !f.contains("_cos")).collect(Collectors.toSet());
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(jsonl))) {
			writer.write(gson.toJson(metadata) + "\n");
			if (lookback != null) writeWithLookBack(source, outputVariable, lookback, header, variableTypes, writer);
			else writeWithoutLookBack(source, outputVariable, header, variableTypes, writer);
		}
		return jsonl;
	}

	private void writeWithoutLookBack(File source, String outputVariable, List<String> header, Map<String, Variable> features, BufferedWriter writer) throws IOException {
		Files.lines(source.toPath())
				.skip(1)
				.map(l -> rowOf(header, l.split("\t")))
				.map(r -> inputDataOf(r, outputVariable, null, features))
				.map(i -> gson.toJson(i) + "\n")
				.forEach(c -> write(c, writer));
	}

	private void writeWithLookBack(File source, String outputVariable, Lookback lookback, List<String> header, Map<String, Variable> features, BufferedWriter writer) throws IOException {
		int lookbackSize = lookback.isWindow() ? lookback.asWindow().size() : 1;
		Queue<Map<String, Double>> queue = new CircularFifoQueue<>(lookbackSize);
		Files.lines(source.toPath())
				.skip(1)
				.map(l -> rowOf(header, l.split("\t")))
				.peek(queue::add)
				.skip(lookbackSize)
				.map(r -> inputDataOf(r, outputVariable, queue, features))
				.map(i -> gson.toJson(i) + "\n")
				.forEach(c -> write(c, writer));
	}

	private static void write(String c, BufferedWriter writer) {
		try {
			writer.write(c);
		} catch (IOException e) {
			Logger.error(e);
		}
	}

	private InputData inputDataOf(Map<String, Double> row, String outputVariable, Queue<Map<String, Double>> queue, Map<String, Variable> features) {
		return new InputData(row.get(outputVariable),
				time(row),
				features(row, features, Variable::isNumeric),
				features(row, features, v -> !v.isNumeric()),
				queue == null ? EMPTY : lookBackTime(queue),
				queue == null ? EMPTY : lookBackFeatures(queue, features, Variable::isNumeric),
				queue == null ? EMPTY : lookBackFeatures(queue, features, v -> !v.isNumeric()));
	}

	private double[][] lookBackTime(Queue<Map<String, Double>> queue) {
		return queue.stream().map(this::time).toArray(double[][]::new);
	}

	private double[][] lookBackFeatures(Queue<Map<String, Double>> queue, Map<String, Variable> features, Predicate<Variable> filter) {
		return queue.stream().map(row -> features(row, features, filter)).toArray(double[][]::new);//TODO check distance
	}

	private double[] time(Map<String, Double> row) {
		return TemporalColumns.get().stream().map(c -> c.name).mapToDouble(row::get).toArray();
	}

	private static double[] features(Map<String, Double> row, Map<String, Variable> features, Predicate<Variable> filter) {
		return row.entrySet().stream().filter(e -> accept(features, e, filter)).mapToDouble(Entry::getValue).toArray();
	}

	private static boolean accept(Map<String, Variable> features, Entry<String, Double> e, Predicate<Variable> filter) {
		Variable variable = features.get(e.getKey());
		return variable != null && filter.test(variable);
	}

	private Map<String, Double> rowOf(List<String> header, String[] values) {
		return IntStream.range(0, header.size()).boxed()
				.collect(toMap(header::get, i -> Double.parseDouble(values[i]), (a, b) -> b));
	}

	private record InputData(double out, double[] t, double[] numerical_t_features, double[] categorical_t_features,
							 double[][] lookback_t, double[][] numerical_lookback_features,
							 double[][] categorical_lookback_features) {
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

	private static ColumnDefinition outputVariable(InferenceModel inference, String name) {
		String colName = name + (inference.timeHorizon() > 0 ? "+" + inference.timeHorizon() : "");
		ColumnDefinition column = new ColumnDefinition(colName, name + ".first");
		if (inference.timeHorizon() > 0) column.add(new LeadFilter(inference.timeHorizon()));
		column.add(new MinMaxNormalizationFilter());
		return column;
	}

	private TemporalAmount period(Resolution resolution) {
		var scale = resolution.scale();
		return scale.ordinal() > Scale.Day.ordinal() ? Duration.of(resolution.amount(), chronoUnitOf(scale)) : periodOf(resolution.amount(), chronoUnitOf(scale));
	}

	private static SubjectHistoryVault subjectVault(File workspace) {
		return new SubjectHistoryVault("jdbc:sqlite:" + new File(workspace, "subjects.ddb"));
	}
}