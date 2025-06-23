package io.picota.digitaltwin.control.commands;

import com.google.gson.Gson;
import io.intino.alexandria.Scale;
import io.intino.alexandria.logger.Logger;
import io.picota.digitaltwin.control.commands.trainvariablescommand.TemporalColumns;
import io.quassar.monentia.picota.DigitalTwin;
import io.quassar.monentia.picota.DigitalTwin.DigitalSubject.InferenceModel;
import io.quassar.monentia.picota.Variable;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import systems.intino.datamarts.subjectstore.SubjectHistory;
import systems.intino.datamarts.subjectstore.SubjectHistoryView;
import systems.intino.datamarts.subjectstore.calculator.model.filters.LeadFilter;
import systems.intino.datamarts.subjectstore.model.signals.NumericalSignal;
import systems.intino.datamarts.subjectstore.view.history.format.ColumnDefinition;

import java.io.*;
import java.nio.file.Files;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.picota.digitaltwin.control.utils.Utils.*;
import static java.util.stream.Collectors.toMap;

public abstract class DataPreparer {
	public static final String TSV = ".tsv";
	public static final String JSONL = ".jsonl";
	public static final String LAYER_SEPARATOR = ":";
	protected final Gson gson = new Gson();
	protected static final double[][] EMPTY = new double[0][0];
	protected final File dataDir;

	public DataPreparer(File dataDir) {
		this.dataDir = dataDir;
	}

	public record InputData(double out, double[] t, double[] numerical_t_features, double[] categorical_t_features,
							double[][] lookback_t, double[][] numerical_lookback_features,
							double[][] categorical_lookback_features) {
	}


	protected File createInitialTsv(DigitalTwin.DigitalSubject.Resolution resolution, InferenceModel inferenceModel,
									String outputVariable, Set<String> outputVariables, Map<String, Variable> features,
									SubjectHistory history) throws IOException {
		File tsv = new File(dataDir, history.name() + "_" + outputVariable + TSV);
		ChronoUnit scale = chronoUnitOf(resolution.scale());
		var timeHorizon = inferenceModel.timeHorizon();
		TemporalAmount period = period(resolution);
		SubjectHistoryView.of(history)
				.from(history.first())
				.to(toPoint(history, scale, timeHorizon))
				.period(period)
				.add(TemporalColumns.get())
				.add(inputVariables(history, inferenceModel, features, outputVariables))
				.add(outputVariable(inferenceModel, outputVariable, history))
				.export()
				.onlyCompleteRows()
				.to(new FileOutputStream(tsv));
		return tsv;
	}

	protected List<ColumnDefinition> inputVariables(SubjectHistory history, InferenceModel inferenceModel, Map<String, Variable> variableTypes, Set<String> outputVariables) {
		boolean prediction = inferenceModel.timeHorizon() > 0;
		return variableTypes.keySet().stream()
				.filter(k -> history.tags().contains(k))
				.filter(t -> prediction || !outputVariables.contains(t))
				.map(tag -> new ColumnDefinition(tag, tag + ".first", typeOf(tag, variableTypes))).toList();
	}

	protected ColumnDefinition outputVariable(InferenceModel inference, String name, SubjectHistory history) {
		String colName = name + (inference.timeHorizon() > 0 ? "+" + inference.timeHorizon() : "");
		NumericalSignal.Summary summary = history.query().number(name).all().summary();
		ColumnDefinition column = new ColumnDefinition(colName, name + ".first");
		if (inference.timeHorizon() > 0) column.add(new LeadFilter(inference.timeHorizon()));
		column.add(new MinMaxNormalization(summary.min().value(), summary.max().value()));
		return column;
	}

	protected TemporalAmount period(DigitalTwin.DigitalSubject.Resolution resolution) {
		var scale = resolution.scale();
		return scale.ordinal() > Scale.Day.ordinal() ?
				Duration.of(resolution.amount(), chronoUnitOf(scale)) :
				periodOf(resolution.amount(), chronoUnitOf(scale));
	}

	protected static ColumnDefinition.Type typeOf(String variable, Map<String, Variable> variableTypes) {
		if (!variableTypes.containsKey(variable)) throw new IllegalArgumentException("Unknown variable: " + variable);
		return variableTypes.get(variable).isNumeric() ?
				ColumnDefinition.Type.Numerical :
				ColumnDefinition.Type.Categorical;
	}

	protected File transformToJsonl(File source, String outputVariable, List<String> header, Map<String, Variable> features, Object metadata, InferenceModel.Lookback lookback) throws IOException {
		File jsonl = new File(source.getParentFile(), source.getName().replace(TSV, JSONL));
		Map<String, Variable> expanded = expandWithCategoricalValues(features);
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(jsonl))) {
			writer.write(gson.toJson(metadata) + "\n");
			if (lookback != null) writeWithLookBack(source, outputVariable, lookback, header, expanded, writer);
			else writeWithoutLookBack(source, outputVariable, header, expanded, writer);
		}
		return jsonl;
	}

	protected Map<String, Variable> expandWithCategoricalValues(Map<String, Variable> features) {
		return features.entrySet().stream()
				.flatMap(e -> {
					String k = e.getKey();
					Variable v = e.getValue();
					if (v.isBoolean())
						return Stream.of("true", "false").map(vl -> new AbstractMap.SimpleEntry<>(k + "_" + vl, v));
					else if (v.isEnumerated())
						return v.asEnumerated().values().stream().map(vl -> new AbstractMap.SimpleEntry<>(k + "_" + vl, v));
					else return Stream.of(e);
				}).collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (k1, k2) -> k1, LinkedHashMap::new));
	}

	protected void writeWithLookBack(File source, String outputVariable, InferenceModel.Lookback lookback, List<String> header, Map<String, Variable> features, BufferedWriter writer) throws IOException {
		int lookbackSize = lookback.isWindow() ? lookback.asWindow().size() : 1;
		Queue<Map<String, Double>> queue = new CircularFifoQueue<>(lookbackSize);
		Files.lines(source.toPath())
				.skip(1)
				.limit(lookbackSize)
				.forEach(l -> queue.add(rowOf(header, l.split("\t"))));
		Files.lines(source.toPath())
				.skip(lookbackSize + 1)
				.map(l -> rowOf(header, l.split("\t")))
				.map(r -> inputDataOf(r, outputVariable, queue, features))
				.map(i -> gson.toJson(i) + "\n")
				.forEach(c -> write(c, writer));
	}

	protected void writeWithoutLookBack(File source, String outputVariable, List<String> header, Map<String, Variable> features, BufferedWriter writer) throws IOException {
		Files.lines(source.toPath())
				.skip(1)
				.map(l -> rowOf(header, l.split("\t")))
				.map(r -> inputDataOf(r, outputVariable, null, features))
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
		return new InputData(row.getOrDefault(outputVariable, 0.),
				time(row),
				features(row, features, outputVariable, Variable::isNumeric),
				features(row, features, outputVariable, v -> !v.isNumeric()),
				queue == null ? EMPTY : lookBackTime(queue),
				queue == null ? EMPTY : lookBackFeatures(queue, features, outputVariable, Variable::isNumeric),
				queue == null ? EMPTY : lookBackFeatures(queue, features, outputVariable, v -> !v.isNumeric()));
	}

	private double[][] lookBackTime(Queue<Map<String, Double>> queue) {
		return queue.stream().map(this::time).toArray(double[][]::new);
	}

	private double[][] lookBackFeatures(Queue<Map<String, Double>> queue, Map<String, Variable> features, String outputVariable, Predicate<Variable> filter) {
		return queue.stream().map(row -> features(row, features, outputVariable, filter)).toArray(double[][]::new); //TODO check distance
	}

	private double[] time(Map<String, Double> row) {
		return TemporalColumns.get().stream().map(c -> c.name).mapToDouble(row::get).toArray();
	}

	private static double[] features(Map<String, Double> row, Map<String, Variable> features, String outputVariable, Predicate<Variable> filter) {
		return row.entrySet().stream()
				.filter(e -> !e.getKey().equals(outputVariable) && accept(features, e, filter))
				.mapToDouble(Map.Entry::getValue)
				.toArray();
	}

	private static boolean accept(Map<String, Variable> features, Map.Entry<String, Double> e, Predicate<Variable> filter) {
		Variable variable = features.get(e.getKey());
		return variable != null && filter.test(variable);
	}

	private Map<String, Double> rowOf(List<String> header, String[] values) {
		return IntStream.range(0, header.size()).boxed()
				.collect(toMap(header::get, i -> Double.parseDouble(values[i]), (a, b) -> b, LinkedHashMap::new));
	}
}
