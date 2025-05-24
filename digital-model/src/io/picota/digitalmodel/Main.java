package io.picota.digitalmodel;

import org.apache.log4j.Level;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import static io.intino.alexandria.logger4j.Logger.setLevel;

public class Main {
	public static void main(String[] args) throws IOException, URISyntaxException {
		setLevel(Level.ERROR);
		run(args);
	}

	public static DigitalModelBox run(String[] args) {
		var configuration = new DigitalModelConfiguration(args);
		var workingDir = new File(configuration.home(), "picota");
		DigitalModelBox box = new DigitalModelBox(configuration, workingDir);
		box.start();
		Runtime.getRuntime().addShutdownHook(new Thread(box::stop));
		return box;
	}
}