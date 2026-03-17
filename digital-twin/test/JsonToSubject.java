import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import systems.intino.datamarts.subjectstore.SubjectHistory;
import systems.intino.datamarts.subjectstore.SubjectHistoryVault;
import systems.intino.datamarts.subjectstore.SubjectHistoryView;
import systems.intino.datamarts.subjectstore.calculator.model.filters.*;
import systems.intino.datamarts.subjectstore.view.history.format.ColumnDefinition;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.picota.digitaltwin.control.utils.Utils.periodOf;
import static java.time.temporal.ChronoUnit.HOURS;

public class JsonToSubject {
	public static final String NAME = "Infecar-Twin";
	private static final File dataDir = new File("digital-twin/test-res");


	public static void main(String[] args) throws IOException {
		SubjectHistoryVault vault = new SubjectHistoryVault("jdbc:sqlite:" + new File("digital-twin/test-res/infecar.ddb").getAbsolutePath());
		SubjectHistory subject = vault.open(DTTest.DIGITAL_TWIN_NAME);
		File source = new File("digital-twin/test-res/infecar.jsonl");
		SubjectHistory.Batch batch = subject.batch();
		Files.lines(source.toPath()).forEach(line -> {
			if (line.contains("\"MET\"")) processMeteo(batch, meteoOf(line));
			else if (line.contains("\"FV\"")) processFV(batch, measurementOf(line));
			else if (line.contains("\"GRID\"")) processGRID(batch, measurementOf(line));
			else if (line.contains("\"CONS\"")) processCONS(batch, measurementOf(line));
		});
		batch.terminate();
		prepareData(vault.open(NAME), HOURS, 2, 6);
		vault.close();
	}

	private static void processFV(SubjectHistory.Batch batch, EnergyMeasurement m) {
		if (m == null) return;
		if (ts(m).atOffset(ZoneOffset.UTC).get(ChronoField.YEAR) < 2020) return;
		SubjectHistory.Transaction t = batch.on(ts(m), "");
		t.put("generation", Arrays.stream(m.power.active).sum());
		t.terminate();
	}

	private static void processGRID(SubjectHistory.Batch batch, EnergyMeasurement m) {
		if (m == null) return;
		if (ts(m).atOffset(ZoneOffset.UTC).get(ChronoField.YEAR) < 2020) return;
		SubjectHistory.Transaction t = batch.on(ts(m), "");
		t.put("sell", Arrays.stream(m.power.active).sum());
		t.terminate();
	}

	private static void processCONS(SubjectHistory.Batch batch, EnergyMeasurement m) {
		if (m == null) return;
		if (ts(m).atOffset(ZoneOffset.UTC).get(ChronoField.YEAR) < 2020) return;
		SubjectHistory.Transaction t = batch.on(ts(m), "");
		t.put("consumption", Arrays.stream(m.power.active).sum());
		t.terminate();
	}

	private static void processMeteo(SubjectHistory.Batch batch, MeteoMeasurement m) {
		if (m == null) return;
		if (ts(m).atOffset(ZoneOffset.UTC).get(ChronoField.YEAR) < 2020) return;
		SubjectHistory.Transaction t = batch.on(ts(m), "");
		t.put("weather_radiation", m.extras.radiation);
		t.put("weather_temperature", m.extras.temp_envi);
		t.put("operational_cellTemperature", m.extras.temp_cell);
		t.terminate();
	}

	private static Instant ts(EnergyMeasurement measurement) {
		return Instant.ofEpochSecond(Long.parseLong(measurement.header.ts));
	}

	private static Instant ts(MeteoMeasurement measurement) {
		return Instant.ofEpochSecond(Long.parseLong(measurement.header.ts));
	}

	public static MeteoMeasurement meteoOf(String json) {
		try {
			return new Gson().fromJson(json, MeteoMeasurement.class);
		} catch (JsonSyntaxException e) {
			return null;
		}
	}

	public static EnergyMeasurement measurementOf(String json) {
		try {
			Gson gson = new Gson();
			return gson.fromJson(json.replace("\"active energy\"", "\"active_energy\"")
					.replace("\"reactive energy\"", "\"reactive_energy\""), EnergyMeasurement.class);
		} catch (JsonSyntaxException e) {
			return null;
		}
	}

	private static void prepareData(SubjectHistory history, ChronoUnit scale, int lag, int timeHorizon) throws IOException {
		if (history == null) {
			System.out.println("No data found for " + history);
			return;
		}
		doPrepareData(history, scale, lag, timeHorizon);
	}

	private static void doPrepareData(SubjectHistory history, ChronoUnit scale, int lag, int timeHorizon) throws IOException {
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
