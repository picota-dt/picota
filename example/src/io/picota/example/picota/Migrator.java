package io.picota.example.picota;

import io.intino.alexandria.Scale;
import io.intino.alexandria.Timetag;
import io.intino.alexandria.datalake.file.FileDatalake;
import io.intino.alexandria.event.Event;
import io.intino.alexandria.event.message.MessageEvent;
import io.intino.alexandria.ingestion.EventSession;
import io.intino.alexandria.ingestion.SessionHandler;
import io.intino.alexandria.logger.Logger;

import java.io.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;

public class Migrator {
	public static final String Tank = "DigitalTwin";
	public static final String SS = "elEspinoDT";
	private final FileDatalake datalake;
	private final File stageDir;
	private final InputStream source;
	private final int[] magnitudesIndices = new int[]{3, 5, 6};

	public Migrator(FileDatalake datalake, File stageDir, InputStream source) {
		this.datalake = datalake;
		this.stageDir = stageDir;
		this.source = source;
	}

	public void run() {
		if (datalake.measurementStore().tank(Tank).content().iterator().hasNext()) return;
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(source))) {
			String[] header = reader.readLine().split(",");
			String[] magnitudes = Arrays.stream(magnitudesIndices).mapToObj(i -> header[i]).toArray(String[]::new);
			SessionHandler handler = new SessionHandler(stageDir);
			EventSession session = handler.createEventSession();
			reader.lines()
					.map(l -> l.split(","))
					.forEach(fields -> save(fields, magnitudes, session));
			session.close();
		} catch (IOException e) {
			Logger.error(e);
		}
	}

	private void save(String[] fields, String[] magnitudes, EventSession session) {
		try {
			Instant timestamp = LocalDateTime.parse(fields[0].replace(" ", "T")).toInstant(ZoneOffset.UTC);
			double[] values = Arrays.stream(magnitudesIndices).mapToDouble(i -> Double.parseDouble(fields[i])).toArray();
			MessageEvent event = new MessageEvent(Tank, SS);
			event.ts(timestamp).toMessage().set("values", values).set("magnitudes", magnitudes);
			session.put(Tank, SS, new Timetag(timestamp, Scale.Day), Event.Format.Message, event);
		} catch (IOException e) {
			Logger.error(e);
		}
	}
}
