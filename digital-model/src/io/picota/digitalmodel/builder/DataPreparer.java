package io.picota.digitalmodel.builder;

import io.intino.alexandria.Scale;
import io.intino.alexandria.logger.Logger;
import io.picota.digitalmodel.TemporalColumns;
import model.DigitalTwin;
import systems.intino.datamarts.subjectstore.SubjectHistory;
import systems.intino.datamarts.subjectstore.SubjectHistoryVault;
import systems.intino.datamarts.subjectstore.SubjectHistoryView;
import systems.intino.datamarts.subjectstore.calculator.model.filters.LagFilter;
import systems.intino.datamarts.subjectstore.calculator.model.filters.MinMaxNormalizationFilter;
import systems.intino.datamarts.subjectstore.view.history.format.ColumnDefinition;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.picota.digitalmodel.utils.Utils.chronoUnitOf;
import static io.picota.digitalmodel.utils.Utils.periodOf;
import static java.time.temporal.ChronoUnit.HOURS;

public class DataPreparer {
	private final File dataDir;
	private final SubjectHistoryVault vault;

	public DataPreparer(File temp, File dataDir) {
		vault = subjectStore(temp);
		this.dataDir = dataDir;
		dataDir.mkdirs();
	}

	void prepareData(DigitalTwin dt, File dataset) throws IOException {
		SubjectHistory history = vault.open(dt.name$());
		if (dataset == null || dataset.length() == 0) {
			Logger.warn("No data found for " + dt.name$());
			return;
		}
		fillHistory(history, dataset);
		checkColumns(dt);
		ChronoUnit scale = chronoUnitOf(dt.resolution().scale());
		var timeHorizon = timeHorizon(dt);
		SubjectHistoryView.of(history)
				.from(history.first().truncatedTo(scale).plus(dt.memory(), scale))
				.to(history.last().plus(1, scale).truncatedTo(HOURS).minus(timeHorizon, scale))
				.period(period(dt.resolution()))
				.add(TemporalColumns.get())
				.add(history.tags().stream().map(DataPreparer::columnOf).toList())
				.add(lagColumns(history, dt.memory()))
				.add(timeHorizonColumns(history, timeHorizon))
				.export()
				.onlyCompleteRows()
				.to(new FileOutputStream(new File(dataDir, history.name() + ".csv")));
	}

	private void checkColumns(DigitalTwin dt) {
		List<String> variables = dt.inferList().stream().map(i -> i.variable().name$()).toList();
		SubjectHistory subject = vault.open(dt.name$());
		if (subject.tags().isEmpty()) return;
		for (String variable : variables) {
			if (!subject.tags().contains(variable)) {
				throw new IllegalStateException("Column " + variable + "not found in the dataset of" + dt.name$());
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

	private static List<ColumnDefinition> lagColumns(SubjectHistory history, int lag) {
		return IntStream.range(1, lag + 1).boxed()
				.flatMap(l -> lagColumns(history.tags(), l))
				.toList();
	}

	private static Stream<ColumnDefinition> lagColumns(List<String> tags, int l) {
		return tags.stream().map(t -> new ColumnDefinition(t + "-" + l, t)
				.add(List.of(new LagFilter(l), new MinMaxNormalizationFilter())));
	}

	private static ColumnDefinition columnOf(String t) {
		return new ColumnDefinition(t, t + ".first").add(new MinMaxNormalizationFilter());
	}

	private static ColumnDefinition columnOf(String name, String source) {
		return new ColumnDefinition(name, source + ".first").add(new MinMaxNormalizationFilter());
	}

	private static int timeHorizon(DigitalTwin dt) {
		return dt.isPredictive() ? dt.asPredictive().timeHorizon() : 0;
	}

	private TemporalAmount period(DigitalTwin.Resolution resolution) {
		var scale = resolution.scale();
		return scale.ordinal() > Scale.Day.ordinal() ? Duration.of(resolution.value(), chronoUnitOf(scale)) : periodOf(resolution.value(), chronoUnitOf(scale));
	}

	private static SubjectHistoryVault subjectStore(File workspace) {
		return new SubjectHistoryVault("jdbc:sqlite:" + new File(workspace, "subjects.ddb"));
	}
}
