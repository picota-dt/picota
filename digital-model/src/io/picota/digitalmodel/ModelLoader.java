package io.picota.digitalmodel;

import model.ModelReader;
import model.PicotaGraph;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModelLoader {
	public PicotaGraph load(URL url) throws IOException {
		return ModelReader.loadFromZip(downloadFile(url).toFile());
	}

	private static Path downloadFile(URL url) throws IOException {
		Path zip = Files.createTempFile("_picota", ".zip");
		Files.write(zip, url.openStream().readAllBytes());
		return zip;
	}
}
