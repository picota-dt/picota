package io.picota.digitalmodel;

import io.picota.digitalmodel.DigitalTwinBuilder.Result.Training;
import io.picota.digitalmodel.setup.TorchScriptsGenerationOperation;
import io.picota.language.model.DigitalTwin;
import io.picota.language.model.PicotaGraph;
import systems.intino.datamarts.subjectstore.SubjectStore;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DigitalModelBox extends AbstractBox {
	public enum State {WaitingData, Training, Prepared, Operating}

	private PicotaGraph graph;
	private File workingDir;
	private DigitalTwinBuilder dtBuilder;
	private DigitalTwinOperator dtOperator;
	private final Map<DigitalTwin, State> states = new HashMap<>();
	private final Map<DigitalTwin, List<Training>> lastTrainings = new HashMap<>();

	private SubjectStore subjectStore;

	public DigitalModelBox(DigitalModelConfiguration conf, PicotaGraph graph, File workingDir) {
		super(conf);
		this.graph = graph;
		this.subjectStore = new SubjectStore("jdbc:sqlite:" + conf.storeUrl());
		this.workingDir = workingDir;
		new TorchScriptsGenerationOperation(workingDir,  graph).execute();
		this.dtBuilder = new DigitalTwinBuilder(subjectStore, workingDir, new File(conf.pythonVenv()));
		this.dtOperator = new DigitalTwinOperator(subjectStore, workingDir, new File(conf.pythonVenv()));
	}

	public DigitalModelBox(String[] args) {
		this(new DigitalModelConfiguration(args));
	}

	public DigitalModelBox(DigitalModelConfiguration configuration) {
		super(configuration);
	}

	@Override
	public io.intino.alexandria.core.Box put(Object o) {
		super.put(o);
		return this;
	}

	public State state(DigitalTwin subject) {
		return states.getOrDefault(subject, State.WaitingData);
	}

	public DigitalTwinBuilder dtBuilder() {
		return dtBuilder;
	}

	public DigitalTwinOperator dtOperator() {
		return dtOperator;
	}

	public DigitalTwin digitalTwin(String name) {
		return graph.digitalTwinList(s -> s.name$().equalsIgnoreCase(name))
				.findFirst().orElse(null);
	}

	public systems.intino.datamarts.subjectstore.model.Subject subject(String name) {
		return subjectStore.open(name);
	}

	public Map<DigitalTwin, List<Training>> lastTrainings() {
		return lastTrainings;
	}

	public void lastTraining(DigitalTwin dt, List<Training> trainings) {
		this.lastTrainings.put(dt, trainings);
	}

	public void state(DigitalTwin subject, State state) {
		states.put(subject, state);
	}

	public void beforeStart() {
	}

	public void afterStart() {
		File dir = new File(workingDir, "models");
		for (DigitalTwin dt : graph.digitalTwinList())
			if (new File(dir, dt.name$()).exists()) states.put(dt, State.Prepared);
	}

	public void beforeStop() {
	}

	public void afterStop() {
	}

}