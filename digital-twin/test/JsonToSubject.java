import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import systems.intino.datamarts.subjectstore.SubjectHistory;
import systems.intino.datamarts.subjectstore.SubjectHistoryVault;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;
import java.util.Arrays;

public class JsonToSubject {

	public static void main(String[] args) throws IOException {
		SubjectHistoryVault vault = new SubjectHistoryVault("jdbc:sqlite:" + new File("digital-model/test-res/infecar.ddb").getAbsolutePath());
		SubjectHistory subject = vault.open(DTTest.DIGITAL_TWIN_NAME);
		File source = new File("digital-model/test-res/infecar.jsonl");
		SubjectHistory.Batch batch = subject.batch();
		Files.lines(source.toPath()).forEach(line -> {
			if (line.contains("\"MET\"")) processMeteo(batch, meteoOf(line));
			else if (line.contains("\"FV\"")) processFV(batch, measurementOf(line));
			else if (line.contains("\"GRID\"")) processGRID(batch, measurementOf(line));
		});
		batch.terminate();
		vault.close();
	}

	private static void processFV(SubjectHistory.Batch batch, EnergyMeasurement m) {
		if (m == null) return;
		if (ts(m).atOffset(ZoneOffset.UTC).get(ChronoField.YEAR) < 2020) return;
		SubjectHistory.Transaction t = batch.on(ts(m), "");
		t.put("generation_reactivePower", Arrays.stream(m.power.reactive).sum());
		t.put("generation_activePower", Arrays.stream(m.power.active).sum());
		t.terminate();
	}

	private static void processGRID(SubjectHistory.Batch batch, EnergyMeasurement m) {
		if (m == null) return;
		if (ts(m).atOffset(ZoneOffset.UTC).get(ChronoField.YEAR) < 2020) return;
		SubjectHistory.Transaction t = batch.on(ts(m), "");
		t.put("consumption_reactivePower", Arrays.stream(m.power.reactive).sum());
		t.put("consumption_activePower", Arrays.stream(m.power.active).sum());
		t.terminate();
	}

	private static void processMeteo(SubjectHistory.Batch batch, MeteoMeasurement m) {
		if (m == null) return;
		if (ts(m).atOffset(ZoneOffset.UTC).get(ChronoField.YEAR) < 2020) return;
		SubjectHistory.Transaction t = batch.on(ts(m), "");
		t.put("weather_radiation", m.extras.radiation);
		t.put("weather_temperature", m.extras.radiation);
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
}
