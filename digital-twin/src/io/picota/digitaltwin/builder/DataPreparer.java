package io.picota.digitaltwin.builder;

import com.google.gson.Gson;
import io.intino.alexandria.Scale;
import io.intino.alexandria.logger.Logger;
import io.picota.digitaltwin.TemporalColumns;
import io.quassar.picota.DigitalTwin.DigitalSubject;
import io.quassar.picota.DigitalTwin.DigitalSubject.InferenceModel;
import io.quassar.picota.DigitalTwin.DigitalSubject.Resolution;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.apache.commons.io.FilenameUtils;
import systems.intino.datamarts.subjectstore.SubjectHistory;
import systems.intino.datamarts.subjectstore.SubjectHistoryVault;
import systems.intino.datamarts.subjectstore.SubjectHistoryView;
import systems.intino.datamarts.subjectstore.calculator.model.filters.LeadFilter;
import systems.intino.datamarts.subjectstore.calculator.model.filters.MinMaxNormalizationFilter;
import systems.intino.datamarts.subjectstore.view.history.format.ColumnDefinition;

import java.io.*;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.picota.digitaltwin.utils.Utils.chronoUnitOf;
import static io.picota.digitaltwin.utils.Utils.periodOf;
import static java.time.temporal.ChronoUnit.HOURS;

public class DataPreparer {
	public static final String TSV = ".tsv";
	public static final String JSONL = ".jsonl";
	public static final String LAYER_SEPARATOR = ":";
	private final File temp;
	private final File dataDir;
	private final Gson gson = new Gson();

	public DataPreparer(File temp, File dataDir) {
		this.temp = temp;
		this.dataDir = dataDir;
		this.dataDir.mkdirs();
	}

	void prepareData(DigitalSubject subject, InferenceModel inferenceModel, File subjectDataset) throws IOException {
		String subjectName = FilenameUtils.removeExtension(subjectDataset.getName());
		SubjectHistoryVault vault = subjectStore(temp);
		SubjectHistory history = vault.open(subjectName);
		fillHistory(history, subjectDataset);
		String[] outputVariables = outputVariables(inferenceModel);
		checkColumns(history, subjectName, outputVariables);
		for (String outputVariable : outputVariables) {
			var timeHorizon = inferenceModel.isPrediction() ? inferenceModel.asPrediction().timeHorizon() : 0;
			var outName = timeHorizon == 0 ? outputVariable : outputVariable + "+" + timeHorizon;
			File file = new File(dataDir, history.name() + "_" + outputVariable + TSV);
			createInitialTsv(subject.resolution(), inferenceModel, outputVariable, history, file);
			File jsonl = transformToJsonl(file, outName, new HashMap<>(Map.of("means", means(history, outName), "stds", stds(history, outName))), inferenceModel.asType().lookBack());
			file.delete();
			transformToTsv(jsonl, outName);
		}
		vault.close();

	}

	private static double[] stds(SubjectHistory history, String outName) {
		return history.tags().stream().filter(t -> !t.equals(outName)).mapToDouble(t -> history.query().number(t).all().summary().sd()).toArray();
	}

	private static double[] means(SubjectHistory history, String outName) {
		return history.tags().stream().filter(t -> !t.equals(outName)).mapToDouble(t -> history.query().number(t).all().summary().mean()).toArray();
	}

	private void createInitialTsv(Resolution resolution, InferenceModel inferenceModel, String outputVariable, SubjectHistory history, File file) throws IOException {
		ChronoUnit scale = chronoUnitOf(resolution.scale());
		var timeHorizon = inferenceModel.isPrediction() ? inferenceModel.asPrediction().timeHorizon() : 0;
		SubjectHistoryView.of(history)
				.from(history.first().truncatedTo(scale))
				.to(history.last().plus(1, scale).truncatedTo(HOURS).minus(timeHorizon, chronoUnitOf(resolution.scale())))
				.period(period(resolution))
				.add(TemporalColumns.get())
				.add(inputVariables(history, inferenceModel, outputVariable))
				.add(outputVariable(inferenceModel, outputVariable))
				.export()
				.onlyCompleteRows()
				.to(new FileOutputStream(file));
	}

	public static String[] outputVariables(InferenceModel inferenceModel) {
		if (inferenceModel.variable().isLayered()) {
			List<String> layers = inferenceModel.layers();
			if (layers == null || layers.isEmpty())
				layers = inferenceModel.variable().asLayered().layerList().stream().flatMap(l -> l.values().stream()).toList();
			return layers.stream().map(l -> inferenceModel.variable().name$() + LAYER_SEPARATOR + l).toArray(String[]::new);
		}
		return new String[]{inferenceModel.variable().name$()};
	}

	private void transformToTsv(File jsonl, String outName) {

	}

	private static List<ColumnDefinition> inputVariables(SubjectHistory history, InferenceModel inferenceModel, String outputVariable) {
		boolean prediction = inferenceModel.isPrediction();
		return history.tags().stream()
				.filter(t -> prediction || !t.equals(outputVariable))
				.map(t1 -> new ColumnDefinition(t1, t1 + ".first")).toList();
	}

	private File transformToJsonl(File source, String outputVariable, Map<String, Object> headerValues,
								  int lookback) throws IOException {
		File jsonl = new File(source.getParentFile(), source.getName().replace(TSV, JSONL));
		String[] header = Files.lines(source.toPath()).findFirst().get().split("\t");
		headerValues.put("input_variables", Arrays.stream(header).filter(f -> !f.equals(outputVariable)).toArray(String[]::new));
		headerValues.put("lookback_size", lookback);
		Set<String> features = Arrays.stream(header).filter(f -> !f.equals(outputVariable) && !f.contains("_sin") && !f.contains("_cos")).collect(Collectors.toSet());
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(jsonl))) {
			writer.write(gson.toJson(headerValues) + "\n");
			if (lookback > 0) writeWithLookBack(source, outputVariable, lookback, header, features, writer);
			else writeWithoutLookBack(source, outputVariable, header, features, writer);
		}
		return jsonl;
	}

	private void writeWithoutLookBack(File source, String outputVariable, String[]
			header, Set<String> features, BufferedWriter writer) throws IOException {
		Files.lines(source.toPath())
				.skip(1)
				.map(l -> rowOf(header, l.split("\t")))
				.map(r -> inputDataOf(r, outputVariable, null, features))
				.map(i -> gson.toJson(i) + "\n")
				.forEach(c -> write(c, writer));
	}

	private void writeWithLookBack(File source, String outputVariable, int lookback, String[]
			header, Set<String> features, BufferedWriter writer) throws IOException {
		Queue<Map<String, Double>> queue = new CircularFifoQueue<>(lookback);
		Files.lines(source.toPath())
				.skip(1)
				.map(l -> rowOf(header, l.split("\t")))
				.peek(queue::add)
				.skip(lookback)
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

	private InputData inputDataOf(Map<String, Double> row, String
			outputVariable, Queue<Map<String, Double>> queue, Set<String> features) {
		return new InputData(row.get(outputVariable), features(row, features), time(row), queue == null ? new double[0][0] : lookBackFeatures(queue, features), queue == null ? new double[0][0] : lookBackTime(queue));
	}

	private double[][] lookBackTime(Queue<Map<String, Double>> queue) {
		return queue.stream().map(this::time).toArray(double[][]::new);
	}

	private double[][] lookBackFeatures(Queue<Map<String, Double>> queue, Set<String> features) {
		return queue.stream().map(row -> features(row, features)).toArray(double[][]::new);
	}

	private double[] time(Map<String, Double> row) {
		return TemporalColumns.get().stream().map(c -> c.name).mapToDouble(row::get).toArray();
	}

	private static double[] features(Map<String, Double> row, Set<String> features) {
		return row.entrySet().stream().filter(e -> features.contains(e.getKey())).mapToDouble(Entry::getValue).toArray();
	}

	private Map<String, Double> rowOf(String[] header, String[] values) {
		return IntStream.range(0, header.length).boxed()
				.collect(Collectors.toMap(i -> header[i], i -> Double.parseDouble(values[i]), (a, b) -> b));
	}

	private record InputData(double out, double[] t_features, double[] t, double[][] lookback_features,
							 double[][] lookback_t) {
	}

	private void checkColumns(SubjectHistory history, String subject, String[] outputVariables) {
		if (history.tags().isEmpty()) return;
		Arrays.stream(outputVariables)
				.filter(variable -> !history.tags().contains(variable))
				.forEach(variable -> {
					throw new IllegalStateException("Column " + variable + " not found in the dataset of " + subject);
				});
	}

	private static void fillHistory(SubjectHistory history, File dataset) throws IOException {
		String firstLine = Files.lines(dataset.toPath()).findFirst().get();
		String separator = firstLine.contains("\t") ? "\t" : ",";
		String[] header = firstLine.split(separator);
		SubjectHistory.Batch batch = history.batch();
		Files.lines(dataset.toPath()).skip(1)
				.map(l -> l.split(separator, -1))
				.forEach(line -> {
					SubjectHistory.Transaction t = batch.on(Instant.parse(line[0]), "");
					for (int i = 1; i < header.length; i++)
						if (!line[i].trim().isEmpty()) t.put(header[i].trim(), Double.parseDouble(line[i].trim()));
					t.terminate();
				});
		batch.terminate();
	}

	private static ColumnDefinition outputVariable(InferenceModel inference, String name) {
		String colName = name + (inference.isPrediction() ? "+" + inference.asPrediction().timeHorizon() : "");
		ColumnDefinition column = new ColumnDefinition(colName, name + ".first");
		if (inference.isPrediction()) column.add(new LeadFilter(inference.asPrediction().timeHorizon()));
		column.add(new MinMaxNormalizationFilter());
		return column;
	}

	private TemporalAmount period(Resolution resolution) {
		var scale = resolution.scale();
		return scale.ordinal() > Scale.Day.ordinal() ? Duration.of(resolution.amount(), chronoUnitOf(scale)) : periodOf(resolution.amount(), chronoUnitOf(scale));
	}

	private static SubjectHistoryVault subjectStore(File workspace) {
		return new SubjectHistoryVault("jdbc:sqlite:" + new File(workspace, "subjects.ddb"));
	}
}