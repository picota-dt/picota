package io.picota.digitaltwin;

import io.picota.digitaltwin.builder.DigitalSubjectBuilder;
import io.picota.digitaltwin.builder.DigitalSubjectBuilder.Result.Training;
import io.picota.digitaltwin.ui.UiService;
import io.quassar.picota.DigitalTwin;
import io.quassar.picota.DigitalTwin.DigitalSubject;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DigitalTwinBox extends AbstractBox {
	public enum State {WaitingData, Training, Prepared, Operating;}

	private File workingDir;
	private DigitalSubjectBuilder dtBuilder;
	private DigitalTwinOperator dtOperator;
	private final Map<DigitalSubject, State> states = new HashMap<>();
	private final Map<DigitalTwin, List<Training>> lastTrainings = new HashMap<>();

	public DigitalTwinBox(DigitalTwinConfiguration conf, File workingDir) {
		super(conf);
		this.workingDir = workingDir;
		this.dtBuilder = new DigitalSubjectBuilder(workingDir, states, new File(conf.pythonVenv()));
		//this.dtOperator = new DigitalTwinOperator(subjectStore, workingDir, new File(conf.pythonVenv()));
	}

	public DigitalTwinBox(String[] args) {
		this(new DigitalTwinConfiguration(args));
	}

	public DigitalTwinBox(DigitalTwinConfiguration configuration) {
		super(configuration);
	}

	@Override
	public io.intino.alexandria.core.Box put(Object o) {
		super.put(o);
		return this;
	}

	public State state(DigitalSubject subject) {
		return states.getOrDefault(subject, State.WaitingData);
	}

	public DigitalSubjectBuilder dtBuilder() {
		return dtBuilder;
	}

	public DigitalTwinOperator dtOperator() {
		return dtOperator;
	}

	public DigitalSubject digitalSubject(String name) {
		return null;
	}

	public void state(DigitalSubject subject, State state) {
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