package io.picota.digitaltwin.model;

import io.picota.digitaltwin.control.commands.Command;
import io.quassar.picota.PicotaGraph;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

public class DigitalTwin {
	private final String id;
	private String name;
	private String version;
	private final File subjectsDirectory;
	private final String url;
	private final Instant createdAt;
	private TrainingReport report;
	private State state;
	private transient Archetype archetype;
	private transient PicotaGraph graph;
	private transient String progressMessage;
	private transient Future<Command.Result<Void>> trainProcess;

	public DigitalTwin(File subjectsDirectory, String url, String id, String name, String version) {
		this.subjectsDirectory = subjectsDirectory;
		this.url = url;
		this.id = id;
		this.name = name;
		this.version = version;
		this.archetype = createArchetype();
		this.createdAt = Instant.now();
		this.state = State.WaitingData;
	}

	public String id() {
		return id;
	}

	public String url() {
		return url;
	}

	public State state() {
		return state;
	}

	public TrainingReport report() {
		return report;
	}

	public DigitalTwin state(State state) {
		this.state = state;
		return this;
	}

	public Instant createdAt() {
		return createdAt;
	}

	public DigitalTwin report(TrainingReport report) {
		this.report = report;
		return this;
	}

	public Archetype archetype() {
		return archetype == null ? (archetype = createArchetype()) : archetype;
	}

	public DigitalTwin graph(PicotaGraph graph) {
		this.graph = graph;
		return this;
	}

	public PicotaGraph graph() {
		return graph;
	}

	public String progressMessage() {
		return progressMessage;
	}

	public DigitalTwin progressMessage(String progressMessage) {
		this.progressMessage = progressMessage;
		return this;
	}

	private Archetype createArchetype() {
		return new Archetype(new File(subjectsDirectory, id));
	}

	public Future<Command.Result<Void>> trainProcess() {
		return trainProcess;
	}

	public void trainProcess(Future<Command.Result<Void>> future) {
		this.trainProcess = future;
	}

	public String name() {
		return this.name;
	}

	public String version() {
		return version;
	}

	public enum State {WaitingData, DownloadedData, PreparingData, Training, TrainFinished, Prepared}

	public record TrainingReport(String model, Future.State state, String report, String errors,
								 List<Variable> trainings,
								 File modelsDir) {

		public record Variable(String dt, String name, double loss, double min, double max,
							   Map<String, Double> contributors) {
		}
	}
}
