package io.picota.runtime;

import io.intino.alexandria.logger4j.Logger;
import io.intino.datahub.box.DataHubBox;
import io.intino.datahub.model.NessGraph;
import org.apache.log4j.Level;

import java.io.File;

public class PicotaStarter {

	public static RuntimeBox start(String[] args, NessGraph graph) {
		var configuration = new RuntimeConfiguration(args);
		var workingDir = new File(configuration.home(), "digital-twins");
		var pythonVenv = new File(configuration.args().get("venv"));
		RuntimeBox runtimeBox = new RuntimeBox(configuration, (DataHubBox) new DataHubBox(args).put(graph), workingDir, pythonVenv);
		Logger.setLevel(Level.ERROR);
		runtimeBox.start();
		return runtimeBox;
	}
}
