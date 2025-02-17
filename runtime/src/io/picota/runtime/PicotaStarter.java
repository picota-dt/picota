package io.picota.runtime;

import io.intino.datahub.box.DataHubBox;

import java.io.File;
import java.io.InputStream;

public class PicotaStarter {
	private final String[] args;
	private final DataHubBox box;
	private final File workingDir;
	private final File pythonVenv;
	private final InputStream scripts;

	public PicotaStarter(String[] args, DataHubBox box, File workingDir, File pythonVenv, InputStream scripts) {
		this.args = args;
		this.box = box;
		this.workingDir = workingDir;
		this.pythonVenv = pythonVenv;
		this.scripts = scripts;
	}

	public void start() {
		RuntimeBox box = new RuntimeBox(args, this.box, workingDir, pythonVenv, scripts);
		box.start();
	}


}
