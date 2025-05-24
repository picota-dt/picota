package io.picota.digitalmodel;

import io.picota.digitalmodel.builder.DigitalSubjectBuilder;
import io.picota.digitalmodel.builder.DigitalSubjectBuilder.Result.Training;
import io.picota.digitalmodel.ui.UiService;
import model.DigitalTwin;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DigitalModelBox extends AbstractBox {
	public enum State {WaitingData, Training, Prepared, Operating;}
	private File workingDir;
	private DigitalSubjectBuilder dtBuilder;
	private DigitalTwinOperator dtOperator;
	private final Map<DigitalTwin, State> states = new HashMap<>();
	private final Map<DigitalTwin, List<Training>> lastTrainings = new HashMap<>();

	public DigitalModelBox(DigitalModelConfiguration conf, File workingDir) {
		super(conf);
		this.workingDir = workingDir;
		this.dtBuilder = new DigitalSubjectBuilder(workingDir, states, new File(conf.pythonVenv()));
		//this.dtOperator = new DigitalTwinOperator(subjectStore, workingDir, new File(conf.pythonVenv()));
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

	public DigitalSubjectBuilder dtBuilder() {
		return dtBuilder;
	}

	public DigitalTwinOperator dtOperator() {
		return dtOperator;
	}

	public DigitalTwin digitalTwin(String name) {
		return null;
	}

	public void state(DigitalTwin subject, State state) {
		states.put(subject, state);
	}

	public File workingDir() {
		return workingDir;
	}

	public void beforeStart() {
		io.intino.alexandria.http.AlexandriaHttpServerBuilder.setup(Integer.parseInt(configuration.apiPort()), "www/");
		io.intino.alexandria.http.AlexandriaHttpServerBuilder.setUI(true);
		new UiService(this).start();
	}

	public void afterStart() {
	}

	public void beforeStop() {
	}

	public void afterStop() {
	}
}