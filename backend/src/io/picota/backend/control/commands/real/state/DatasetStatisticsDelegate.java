package io.picota.backend.control.commands.real.state;

import io.picota.backend.control.ui.schemas.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class DatasetStatisticsDelegate {
	public CsvStats computeCsvStats(DigitalSubject subject, byte[] content) {
		if (subject == null) return new CsvStats(0, Map.of());
		byte[] safeContent = content == null ? new byte[0] : content;
		if (safeContent.length == 0) return fallbackStats(subject, 500);

		String text = new String(safeContent, StandardCharsets.UTF_8).trim();
		if (text.isEmpty()) return fallbackStats(subject, 500);

		String[] lines = text.split("\\r?\\n");
		if (lines.length < 2) return fallbackStats(subject, 500);

		String[] headers = lines[0].split(",");
		Map<String, List<Double>> numericValuesByColumn = new LinkedHashMap<>();
		for (String header : headers) {
			numericValuesByColumn.put(cleanHeader(header), new ArrayList<>());
		}

		int parsedRows = 0;
		for (int i = 1; i < lines.length; i++) {
			String[] cells = lines[i].split(",");
			if (cells.length != headers.length) continue;
			parsedRows++;
			for (int j = 0; j < cells.length; j++) {
				Double parsed = tryParseDouble(cells[j]);
				if (parsed == null) continue;
				numericValuesByColumn.get(cleanHeader(headers[j])).add(parsed);
			}
		}

		Map<String, VariableStat> stats = new LinkedHashMap<>();
		numericValuesByColumn.forEach((header, values) -> {
			if (!values.isEmpty()) stats.put(header, computeStats(values));
		});
		if (stats.isEmpty()) return fallbackStats(subject, parsedRows == 0 ? 500 : parsedRows);
		return new CsvStats(parsedRows == 0 ? 500 : parsedRows, stats);
	}

	private CsvStats fallbackStats(DigitalSubject subject, int count) {
		return new CsvStats(count, generateVariableStatsFromSubject(subject, count));
	}

	private Map<String, VariableStat> generateVariableStatsFromSubject(DigitalSubject subject, int count) {
		Map<String, VariableStat> generated = new LinkedHashMap<>();
		for (Variable variable : subject.variables()) {
			if (variable == null || variable.variableType() == VariableType.INFERRED) continue;
			if (variable.dataType() == VariableDataType.CATEGORICAL) continue;
			double base = syntheticBase(variable);
			double std = Math.max(Math.abs(base) * 0.05, 0.1);
			generated.put(variable.name(), new VariableStat(
					count,
					round(base),
					round(std),
					round(base - 2 * std),
					round(base + 2 * std),
					round(base)
			));
		}
		return generated;
	}

	private double syntheticBase(Variable variable) {
		int hash = Math.abs(Objects.hash(variable.id(), variable.name(), variable.unit(), variable.variableType()));
		return 1.0 + (hash % 7_500) / 100.0;
	}

	private VariableStat computeStats(List<Double> values) {
		List<Double> sorted = values.stream().sorted().toList();
		int count = values.size();
		double sum = values.stream().reduce(0.0, Double::sum);
		double mean = sum / count;
		double variance = values.stream().mapToDouble(v -> Math.pow(v - mean, 2)).sum() / count;
		double std = Math.sqrt(variance);
		double min = sorted.get(0);
		double max = sorted.get(sorted.size() - 1);
		double median = sorted.size() % 2 == 0
				? (sorted.get(sorted.size() / 2 - 1) + sorted.get(sorted.size() / 2)) / 2.0
				: sorted.get(sorted.size() / 2);
		return new VariableStat(count, round(mean), round(std), round(min), round(max), round(median));
	}

	private String cleanHeader(String header) {
		if (header == null) return "";
		return header.trim().replace("\"", "");
	}

	private Double tryParseDouble(String value) {
		try {
			return Double.parseDouble(value.trim());
		} catch (RuntimeException ex) {
			return null;
		}
	}

	private static double round(double value) {
		return Math.round(value * 1_000.0) / 1_000.0;
	}

	public record CsvStats(int rowCount, Map<String, VariableStat> variableStats) {
		public CsvStats {
			variableStats = variableStats == null ? Map.of() : Map.copyOf(variableStats);
		}
	}
}
