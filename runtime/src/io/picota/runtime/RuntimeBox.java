package io.picota.runtime;

import io.intino.alexandria.logger.Logger;
import io.intino.datahub.box.DataHubBox;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class RuntimeBox extends AbstractBox {
	public enum State {Waiting, Training, Prepared, Operating;}

	private DataHubBox datahub;
	private File workingDir;
	private File pythonVenv;
	private File scripts;
	private DigitalTwinBuilder dtBuilder;
	private State state = State.Waiting;

	public RuntimeBox(String[] args, DataHubBox box, File workingDir, File pythonVenv, InputStream scripts) {
		super(args);
		this.datahub = box;
		this.workingDir = workingDir;
		this.pythonVenv = pythonVenv;
		this.scripts = new File(workingDir, "scripts");
		this.scripts.mkdirs();
		untar(scripts);
		this.dtBuilder = new DigitalTwinBuilder(box, workingDir, pythonVenv, this.scripts);
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

	public State state() {
		return state;
	}

	public DigitalTwinBuilder dtBuilder() {
		return dtBuilder;
	}

	public DataHubBox datahub() {
		return datahub;
	}

	public File workingDir() {
		return workingDir;
	}

	public File pythonVenv() {
		return pythonVenv;
	}

	public RuntimeBox state(State state) {
		this.state = state;
		return this;
	}

	public void beforeStart() {
	}

	public void afterStart() {
	}

	public void beforeStop() {
	}

	public void afterStop() {
	}

	private void untar(InputStream scripts) {
		try {
			Tar.extractTarFile(scripts, this.scripts);
		} catch (IOException e) {
			Logger.error(e);
		}
	}
}