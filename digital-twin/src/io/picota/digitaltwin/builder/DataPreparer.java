package io.picota.digitaltwin.builder;

import com.google.gson.Gson;
import io.intino.alexandria.Scale;
import io.intino.alexandria.logger.Logger;
import io.picota.digitaltwin.TemporalColumns;
import io.quassar.DigitalTwin;
import io.quassar.DigitalTwin.DigitalSubject;
import io.quassar.DigitalTwin.DigitalSubject.InferenceModel;
import io.quassar.Variable;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import systems.intino.datamarts.subjectstore.SubjectHistory;
import systems.intino.datamarts.subjectstore.SubjectHistoryVault;
import systems.intino.datamarts.subjectstore.SubjectHistoryView;
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
	private final File dataDir;
	private final SubjectHistoryVault vault;

	public DataPreparer(File temp, File dataDir) {
		vault = subjectStore(temp);
		this.dataDir = dataDir;
		dataDir.mkdirs();
	}

	void prepareData(DigitalSubject subject, InferenceModel inferenceModel, File dataset) throws IOException {
		SubjectHistory history = vault.open(subject.name$());
		if (dataset == null || dataset.length() == 0) {
			Logger.warn("No data found for " + subject.name$());
			return;
		}
		fillHistory(history, dataset);
		checkColumns(subject);
		ChronoUnit scale = chronoUnitOf(subject.resolution().scale());
		var timeHorizon = inferenceModel.isPrediction() ? inferenceModel.asPrediction().timeHorizon() : 0;
		File file = new File(dataDir, history.name() + "_" + inferenceModel.variable().name$() + ".csv");
		SubjectHistoryView.of(history)
				.from(history.first().truncatedTo(scale))
				.to(history.last().plus(1, scale).truncatedTo(HOURS).minus(timeHorizon, scale))
				.period(period(subject.resolution()))
				.add(TemporalColumns.get())
				.add(history.tags().stream().map(DataPreparer::columnOf).toList())
				.add(timeHorizonColumns(history, timeHorizon))
				.export()
				.onlyCompleteRows()
				.to(new FileOutputStream(file));
		double[] means = history.tags().stream().mapToDouble(t -> history.query().number(t).all().summary().mean()).toArray();
		double[] stds = history.tags().stream().mapToDouble(t -> history.query().number(t).all().summary().sd()).toArray();
		Map<String, double[]> normal = Map.of("means", means, "stds", stds);
		transformToJsonl(file, inferenceModel.variable(), normal, inferenceModel.asType().lookBack());
	}

	private void transformToJsonl(File source, Variable outputVariable, Map<String, double[]> normal, int lookback) throws IOException {
		File jsonl = new File(source.getParentFile(), source.getName().replace(".csv", ".jsonl"));
		String[] header = Files.lines(source.toPath()).findFirst().get().split("\t");
		Set<String> features = Arrays.stream(header).filter(f -> !f.contains("+") && !f.equals(outputVariable.name$()) && !f.contains("_sin") && !f.contains("_cos")).collect(Collectors.toSet());
		BufferedWriter writer = new BufferedWriter(new FileWriter(jsonl));
		writer.write(new Gson().toJson(normal) + "\n");
		if (lookback > 0) writeWithLookBack(source, outputVariable, lookback, header, features, writer);
		else writeWithoutLookBack(source, outputVariable, header, features, writer);
		writer.close();
	}

	private void writeWithoutLookBack(File source, Variable outputVariable, String[] header, Set<String> features, BufferedWriter writer) throws IOException {
		Gson gson = new Gson();
		Files.lines(source.toPath())
				.skip(1)
				.map(l -> rowOf(header, l.split("\t")))
				.map(r -> inputDataOf(r, outputVariable.name$(), null, features))
				.map(i -> gson.toJson(i) + "\n")
				.forEach(c -> write(c, writer));
	}

	private void writeWithLookBack(File source, Variable outputVariable, int lookback, String[] header, Set<String> features, BufferedWriter writer) throws IOException {
		Gson gson = new Gson();
		Queue<Map<String, Double>> queue = new CircularFifoQueue<>(lookback);
		Files.lines(source.toPath())
				.skip(1)
				.map(l -> rowOf(header, l.split("\t")))
				.peek(queue::add)
				.skip(lookback)
				.map(r -> inputDataOf(r, outputVariable.name$(), queue, features))
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

	private InputData inputDataOf(Map<String, Double> row, String outputVariable, Queue<Map<String, Double>> queue, Set<String> features) {
		return new InputData(row.get(outputVariable), features(row, features), time(row), queue == null ? new double[0] : lookBackFeatures(queue, features), queue == null ? new double[0] : lookBackTime(queue));
	}

	private double[] lookBackTime(Queue<Map<String, Double>> queue) {
		return queue.stream().flatMapToDouble(row -> Arrays.stream(time(row))).toArray();
	}

	private double[] lookBackFeatures(Queue<Map<String, Double>> queue, Set<String> features) {
		return queue.stream().flatMapToDouble(row -> Arrays.stream(features(row, features))).toArray();
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

	private record InputData(double out, double[] t_features, double[] t, double[] lookback_features,
							 double[] lookback_t) {
	}

	private void checkColumns(DigitalSubject subject) {
		List<String> variables = subject.inferenceModelList().stream().map(i -> i.variable().name$()).toList();
		SubjectHistory history = vault.open(subject.name$());
		if (history.tags().isEmpty()) return;
		for (String variable : variables) {
			if (!history.tags().contains(variable)) {
				throw new IllegalStateException("Column " + variable + "not found in the dataset of" + subject.name$());
			}
		}
	}

	private static void fillHistory(SubjectHistory history, File dataset) throws IOException {
		String firstLine = Files.lines(dataset.toPath()).findFirst().get();
		String separator = firstLine.contains("\t") ? "\t" : ",";
		String[] header = firstLine.split(separator);
		SubjectHistory.Batch batch = history.batch();
		Files.lines(dataset.toPath()).skip(1).map(l -> l.split(separator)).forEach(line -> {
			SubjectHistory.Transaction t = batch.on(Instant.parse(line[0]), "");
			for (int i = 1; i < header.length; i++)
				if (!line[i].trim().isEmpty()) t.put(header[i].trim(), Double.parseDouble(line[i].trim()));
			t.terminate();
		});
		batch.terminate();
	}

	private static List<ColumnDefinition> timeHorizonColumns(SubjectHistory history, int timeHorizon) {
		return history.tags().stream()
				.map(t -> columnOf(t + "+" + timeHorizon, t).add())
				.toList();
	}

	private static ColumnDefinition columnOf(String t) {
		return new ColumnDefinition(t, t + ".first");
	}

	private static ColumnDefinition columnOf(String name, String source) {
		return new ColumnDefinition(name, source + ".first");
	}

	private TemporalAmount period(DigitalTwin.DigitalSubject.Resolution resolution) {
		var scale = resolution.scale();
		return scale.ordinal() > Scale.Day.ordinal() ? Duration.of(resolution.amount(), chronoUnitOf(scale)) : periodOf(resolution.amount(), chronoUnitOf(scale));
	}

	private static SubjectHistoryVault subjectStore(File workspace) {
		return new SubjectHistoryVault("jdbc:sqlite:" + new File(workspace, "subjects.ddb"));
	}
}