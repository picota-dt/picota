package io.picota.runtime;

import io.intino.alexandria.ui.services.AuthService;
import io.intino.datahub.box.DataHubBox;
import io.intino.datahub.model.Sensor;

import java.io.File;
import java.net.URL;

import static java.util.Objects.requireNonNull;

public class RuntimeBox extends AbstractBox {


	public enum State {Waiting, Training, Prepared, Operating;}

	private DataHubBox datahub;
	private File workingDir;
	private File pythonVenv;
	private DigitalTwinBuilder dtBuilder;
	private DigitalTwinOperator dtOperator;
	private State state = State.Waiting;
	private boolean runningDataHub = false;

	public RuntimeBox(RuntimeConfiguration args, DataHubBox datahub, File workingDir, File pythonVenv) {
		super(args);
		this.datahub = datahub;
		this.workingDir = workingDir;
		this.pythonVenv = pythonVenv;
		this.dtBuilder = new DigitalTwinBuilder(datahub, workingDir, pythonVenv);
		this.dtOperator = new DigitalTwinOperator(datahub, workingDir, pythonVenv);
	}

	public RuntimeBox(String[] args) {
		this(new RuntimeConfiguration(args));
	}

	public RuntimeBox(RuntimeConfiguration configuration) {
		super(configuration);
	}

	@Override
	public io.intino.alexandria.core.Box put(Object o) {
		super.put(o);
		if (o instanceof DataHubBox d) this.datahub = d;
		return this;
	}

	public Sensor entity(String name) {
		return datahub().graph()
				.sensorList(s -> s.name$().equalsIgnoreCase(name))
				.findFirst().orElse(null);
	}

	public State state() {
		return state;
	}

	public DigitalTwinBuilder dtBuilder() {
		return dtBuilder;
	}

	public DigitalTwinOperator dtOperator() {
		return dtOperator;
	}

	public DataHubBox datahub() {
		return datahub;
	}

	public RuntimeBox state(State state) {
		this.state = state;
		return this;
	}

	public void beforeStart() {
		startDatahub();
	}

	public void startDatahub() {
		if (datahub() != null && !runningDataHub) {
			datahub().start();
			runningDataHub = true;
		}
	}

	public void stopDatahub() {
		if (datahub() != null && runningDataHub) {
			datahub().stop();
			runningDataHub = false;
		}
	}

	public void afterStart() {
		File file = new File(workingDir, "models");
		if (file.exists() && requireNonNull(file.listFiles()).length > 0) state = State.Prepared;

	}

	public void beforeStop() {
	}

	public void afterStop() {
		datahub().stop();
	}

	@Override
	protected AuthService authService(URL authServiceUrl) {
		return null;
	}
}