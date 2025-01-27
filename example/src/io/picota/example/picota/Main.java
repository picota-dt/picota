package io.picota.example.picota;

import io.intino.alexandria.logger4j.Logger;
import io.intino.datahub.box.DataHubBox;
import io.intino.datahub.box.DataHubConfiguration;
import io.picota.runtime.DigitalTwinBuilder;
import org.apache.log4j.Level;

import java.io.File;

public class Main {
	public static void main(String[] args) {
		DataHubConfiguration configuration = new DataHubConfiguration(args);
		DataHubBox box = (DataHubBox) new DataHubBox(args).put(GraphLoader.load(configuration));
		Logger.setLevel(Level.ERROR);
		new Migrator(box.datalake(),box.stageDirectory(), Main.class.getResourceAsStream("/bolivia.csv")).run();
		box.seal();
		new DigitalTwinBuilder(box, new File(configuration.home(), "digital-twins"), new File(configuration.args().get("venv")), Main.class.getResourceAsStream("/scripts.tar")).build();
		box.start();
		Runtime.getRuntime().addShutdownHook(new Thread(box::stop));
	}
}