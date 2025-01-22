package io.picota.example.picota;

import io.intino.alexandria.Scale;
import io.intino.alexandria.Timetag;
import io.intino.alexandria.event.Event;
import io.intino.alexandria.event.measurement.MeasurementEvent;
import io.intino.alexandria.ingestion.EventSession;
import io.intino.alexandria.ingestion.SessionHandler;
import io.intino.alexandria.logger.Logger;

import java.io.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;

public class Migrator {
	private final File temp;
	private final File stageDir;
	private final InputStream source;
	private final int[] magnitudesIndices = new int[]{3, 5, 6};

	public Migrator(File temp, File stageDir, InputStream source) {
		this.temp = temp;
		this.stageDir = stageDir;
		this.source = source;

	}

	public void run() {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(source))) {
			String[] header = reader.readLine().split(",");
			String[] magnitudes = Arrays.stream(magnitudesIndices).mapToObj(i -> header[i]).toArray(String[]::new);
			SessionHandler handler = new SessionHandler(temp);
			EventSession session = handler.createEventSession();
			reader.lines()
					.map(l -> l.split(","))
					.forEach(fields -> save(fields, magnitudes, session));
			session.close();
			handler.pushTo(stageDir);
		} catch (IOException e) {
			Logger.error(e);
		}
	}

	private void save(String[] fields, String[] magnitudes, EventSession session)  {
		try {
			Instant timestamp = LocalDateTime.parse(fields[0].replace(" ", "T")).toInstant(ZoneOffset.UTC);
			double[] values = Arrays.stream(magnitudesIndices).mapToDouble(i -> Double.parseDouble(fields[i])).toArray();
			MeasurementEvent event = new MeasurementEvent("elEspinoDT", "legacy", timestamp, magnitudes, values);
			session.put("elEspinoDT", "legacy", new Timetag(timestamp, Scale.Hour), Event.Format.Measurement, event);
		} catch (IOException e) {
			Logger.error(e);
		}
	}
}
