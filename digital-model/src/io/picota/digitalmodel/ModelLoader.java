package io.picota.digitalmodel;

import model.ModelReader;
import model.PicotaGraph;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModelLoader {
	public PicotaGraph load(File home, URL url) throws IOException {
		return ModelReader.loadFromZip(downloadFile(url, home).toFile());
	}

	private static Path downloadFile(URL url, File home) throws IOException {
		byte[] bytes = url.openStream().readAllBytes();
		Path zip = Files.createTempFile("_picota", ".zip");
		File sources = new File(home, "project");
		sources.mkdirs();
		Files.write(zip, bytes);
		return zip;
	}
}
