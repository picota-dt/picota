package io.picota.digitaltwin.model;

import java.io.File;

public record Archetype(File dir) {

	public Archetype {
		dir.mkdirs();
	}

	public File modelFile() {
		return new File(dir, "model.zip");
	}

	public File reportFile() {
		return new File(dir, "report.pdf");
	}

	public File trainedVariablesDirectory() {
		File models = new File(dir, "models");
		models.mkdirs();
		return models;
	}

	public File dataDirectory() {
		File data = new File(dir, "data");
		data.mkdirs();
		return data;
	}

	public File metadataFile(String subject, String variable) {
		return new File(dataDirectory(), subject + "_" + variable + ".md");
	}

	public File rawDataDirectory() {
		File data = new File(dataDirectory(), "raw");
		data.mkdirs();
		return data;
	}

	public File tempDirectory() {
		File temp = new File(dir, "temp");
		temp.mkdirs();
		return temp;
	}

	public File scriptsDirectory() {
		return new File(dir, "scripts");
	}

	public File trainerScriptsDirectory() {
		File dir = new File(scriptsDirectory(), "trainer");
		dir.mkdirs();
		return dir;
	}

	public File evaluatorScriptsDirectory() {
		File dir = new File(scriptsDirectory(), "evaluator");
		dir.mkdirs();
		return dir;
	}

	public boolean hasRawData() {
		File dir = rawDataDirectory();
		if (!dir.exists()) return false;
		String[] list = dir.list((dir1, name) -> name.endsWith("tsv") || name.endsWith("csv") && !name.startsWith(".") && new File(dir1, name).length() > 0);
		return list != null && list.length != 0;
	}
}
