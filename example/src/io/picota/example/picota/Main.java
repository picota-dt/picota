package io.picota.example.picota;

import io.intino.alexandria.logger4j.Logger;
import io.intino.datahub.box.DataHubBox;
import io.intino.datahub.box.DataHubConfiguration;
import io.intino.magritte.framework.Layer;
import io.picota.runtime.DigitalTwinBuilder;
import org.apache.log4j.Level;

import java.io.File;
import java.io.IOException;

public class Main {
	public static void main(String[] args) throws IOException {
		DataHubConfiguration configuration = new DataHubConfiguration(args);
		DataHubBox box = (DataHubBox) new DataHubBox(args).put(GraphLoader.load(configuration).core$());
		Logger.setLevel(Level.ERROR);
		new InfecarDataPreparer(box.datalake(),box.stageDirectory(), Main.class.getResourceAsStream("/infecar.jsonl")).clean();
		box.start();
		dtBuilder(box, configuration).build(box.graph().sensorList().stream().map(Layer::name$).toArray(String[]::new));
		Runtime.getRuntime().addShutdownHook(new Thread(box::stop));
	}

	private static DigitalTwinBuilder dtBuilder(DataHubBox box, DataHubConfiguration configuration) {
		return new DigitalTwinBuilder(box, new File(configuration.home(), "digital-twins"), new File(configuration.args().get("venv")), Main.class.getResourceAsStream("/scripts.tar"));
	}
}