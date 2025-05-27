import org.junit.Test;
import systems.intino.datamarts.subjectstore.SubjectHistory;
import systems.intino.datamarts.subjectstore.SubjectHistoryVault;
import systems.intino.datamarts.subjectstore.SubjectHistoryView;
import systems.intino.datamarts.subjectstore.calculator.model.filters.*;
import systems.intino.datamarts.subjectstore.view.history.format.ColumnDefinition;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.picota.digitaltwin.utils.Utils.periodOf;
import static java.time.temporal.ChronoUnit.HOURS;

public class TsvGenerationTest {
	public static final String NAME = "Infecar-Twin";
	private final File dataDir = new File("./temp/data");
	private final String subjectPath = "./test-res/infecar.ddb";

	@Test
	public void shouldGenerateTsv() throws IOException {
		dataDir.mkdirs();
		SubjectHistoryVault vault = new SubjectHistoryVault("jdbc:sqlite:" + subjectPath);
		prepareData(vault.open(NAME), HOURS, 2, 6);
		vault.close();
	}

	private void prepareData(SubjectHistory history, ChronoUnit scale, int lag, int timeHorizon) throws IOException {
		if (history == null) {
			System.out.println("No data found for " + history);
			return;
		}
		doPrepareData(history, scale, lag, timeHorizon);
	}

	private void doPrepareData(SubjectHistory history, ChronoUnit scale, int lag, int timeHorizon) throws IOException {
		TemporalAmount period = temporalAmount(1, scale);
		SubjectHistoryView.of(history)
				.from(history.first().truncatedTo(scale))
				.to(history.last().truncatedTo(HOURS))
				.period(period)
				.add(TemporalColumns.get())
				.add(history.tags().stream().map(name -> new ColumnDefinition(name, name + ".average", new MinMaxNormalizationFilter())).toList())
				.add(lagColumns(history, lag))
				.add(timeHorizonColumns(history, timeHorizon))
				.export().onlyCompleteRows().to(new FileOutputStream(new File(dataDir, history.name() + ".csv")));
	}

	private static List<ColumnDefinition> lagColumns(SubjectHistory history, int lag) {
		return IntStream.range(1, lag + 1).boxed()
				.flatMap(l -> lagColumns(history.tags(), l))
				.toList();
	}

	private static List<ColumnDefinition> timeHorizonColumns(SubjectHistory history, int timeHorizon) {
		return history.tags().stream()
				.map(t -> new ColumnDefinition(t + "+" + timeHorizon, t, new LeadFilter(timeHorizon), new MinMaxNormalizationFilter()))
				.toList();
	}

	private static Stream<ColumnDefinition> lagColumns(List<String> tags, int l) {
		return tags.stream().map(t -> new ColumnDefinition(t + "-" + l, t, new LagFilter(l), new MinMaxNormalizationFilter()));
	}

	private static TemporalAmount temporalAmount(int resolution, ChronoUnit scale) {
		return scale.ordinal() < ChronoUnit.DAYS.ordinal() ? Duration.of(resolution, scale) : periodOf(resolution, scale);
	}

	public static class TemporalColumns {

		public static List<ColumnDefinition> get() {
			return List.of(
					new ColumnDefinition("month_sin", "ts.month-of-year").add(new SinFilter()),
					new ColumnDefinition("month_cos", "ts.month-of-year").add(new CosFilter()),
					new ColumnDefinition("day_sin", "ts.day-of-month").add(new SinFilter()),
					new ColumnDefinition("day_cos", "ts.day-of-month").add(new CosFilter()),
					new ColumnDefinition("hour_sin", "ts.hour-of-day").add(new SinFilter()),
					new ColumnDefinition("hour_cos", "ts.hour-of-day").add(new CosFilter()),
					new ColumnDefinition("quarter_sin", "ts.quarter-of-year").add(new SinFilter()),
					new ColumnDefinition("quarter_cos", "ts.quarter-of-year").add(new CosFilter())
			);
		}
	}
}
