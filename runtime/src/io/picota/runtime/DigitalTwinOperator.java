package io.picota.runtime;

import io.intino.alexandria.jms.TopicProducer;
import io.intino.alexandria.logger.Logger;
import io.intino.datahub.box.DataHubBox;
import io.intino.datahub.model.Sensor;
import io.intino.sumus.chronos.TimeSeries;
import io.intino.sumus.chronos.TimelineStore;
import jakarta.jms.Message;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.apache.commons.io.FileUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import static io.picota.runtime.CsvUtils.*;
import static java.util.Arrays.stream;

public class DigitalTwinOperator {
	private final DataHubBox datahub;
	private final File sourcesDir;
	private final File pythonVenv;
	private final File modelsDir;
	private final List<Sensor> sensors;
	private final File dataDir;
	private final Object monitor = new Object();
	private boolean subscribed;

	public DigitalTwinOperator(DataHubBox datahub, File workingDir, File pythonVenv) {
		this.datahub = datahub;
		this.modelsDir = new File(workingDir, "models");
		this.sourcesDir = new File(workingDir, "sources");
		this.dataDir = new File(workingDir, "data");
		this.pythonVenv = pythonVenv;
		if (this.sourcesDir.exists()) clean();
		this.sourcesDir.mkdirs();
		this.modelsDir.mkdirs();
		this.dataDir.mkdirs();
		this.sensors = datahub.graph().sensorList();
		this.subscribed = false;
	}

	public void start() {
		evaluate();
//		registerOnChanges();
	}

	private void registerOnChanges() {
		if (subscribed) return;
		synchronized (monitor) {
			datahub.datamarts().get("master").timelineStore().subscribedEvents()
					.forEach(e -> datahub.brokerService().manager().
							registerTopicConsumer(e, m -> inferAndPublish(sensorOf(m))));
			subscribed = true;
		}
	}

	private void evaluate() {
		synchronized (monitor) {
			untar(this.getClass().getResourceAsStream("/evaluators.tar"), sourcesDir);
			for (Sensor digitalTwin : sensors) {
				Logger.info("Starting DT " + digitalTwin.name$() + "...");
				inferAndPublish(digitalTwin);
			}
		}
	}

	private void inferAndPublish(Sensor digitalTwin) {
		try {
			TimelineStore timelineStore = digitalTwin(digitalTwin.name$());
			if (timelineStore == null || timelineStore.timeline().last() == null) return;
			File data = prepareData(digitalTwin, timelineStore);
			publish(digitalTwin, denormalize(timelineStore, inferDt(digitalTwin, data)));
		} catch (Exception e) {
			Logger.error(e);
		}
	}

	private List<Inference> inferDt(Sensor dt, File data) throws IOException, InterruptedException {
		Logger.info("Inferring digital twin: " + dt.name$());
		String pythonExecutable = pythonVenv.getAbsolutePath() + "/bin/python";
		File scriptPath = new File(sourcesDir, dt.name$() + ".py");
		if (!scriptPath.exists()) throw new IOException("Main script not found: " + scriptPath.getAbsolutePath());
		Process process = new ProcessBuilder(pythonExecutable, scriptPath.getAbsolutePath(), modelsDir.getAbsolutePath(), data.getAbsolutePath())
				.directory(sourcesDir)
				.redirectErrorStream(true)
				.start();
		String result = new String(process.getInputStream().readAllBytes());
		Logger.info("Finished evaluation of variable. Code: " + process.waitFor() + ". Result:\n" + result);
		if (process.exitValue() != 0) throw new IOException(result.trim());
		return result.lines()
				.map(line -> line.trim().split("\t"))
				.map(f -> new Inference(f[0], Double.parseDouble(f[1]))).toList();
	}

	private Sensor sensorOf(Message m) {
		return null;
	}

	private File prepareData(Sensor dt, TimelineStore tl) {
		File dataFile = new File(dataDir, dt.name$() + ".csv");
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(dataFile))) {
			writeHeader(dt, tl, writer);
			writeValuesForInfer(dt, tl, writer);
		} catch (IOException e) {
			Logger.error(e);
		}
		return dataFile;
	}

	private List<Inference> denormalize(TimelineStore timelineStore, List<Inference> inferences) {
		return inferences.stream().map(i -> {
			try {
				TimeSeries points = timelineStore.timeline().get(i.variable);
				return new Inference(i.variable, denormalize(i.value, stream(points.values).min().getAsDouble(), stream(points.values).max().getAsDouble()));
			} catch (IOException e) {
				Logger.error(e);
				return i;
			}
		}).toList();
	}

	public static double denormalize(double x, double min, double max) {
		return x * (max - min) + min;
	}

	private static void writeHeader(Sensor digitalTwin, TimelineStore timelineStore, BufferedWriter writer) throws IOException {
		writer.write(headerForInfer(digitalTwin, timelineStore));
	}

	private void publish(Sensor digitalTwin, List<Inference> inference) {
		try {
			TopicProducer producer = datahub.brokerService().manager().topicProducerOf("inference." + digitalTwin.name$());
			ActiveMQTextMessage message = new ActiveMQTextMessage();
			message.setText(messageOf(digitalTwin, inference).toString());
			producer.produce(message);
		} catch (Exception e) {
			Logger.error(e);
		}
	}

	private io.intino.alexandria.message.Message messageOf(Sensor digitalTwin, List<Inference> inference) {
		var message = new io.intino.alexandria.message.Message(digitalTwin.name$());
		message.set("digitalTwin", digitalTwin.name$());
		inference.forEach(i -> message.set(i.variable, i.value()));
		return message;
	}


	public record Inference(String variable, double value) {
	}

	private TimelineStore digitalTwin(String name) {
		return datahub.datamarts().get("master").timelineStore().get(name, name);
	}

	private void clean() {
		try {
			FileUtils.deleteDirectory(this.sourcesDir);
		} catch (IOException e) {
			Logger.error(e);
		}
	}
}
