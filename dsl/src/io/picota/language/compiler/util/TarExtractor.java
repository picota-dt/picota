package io.picota.language.compiler.util;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import java.io.*;

public class TarExtractor {

	public static void extractTarFile(InputStream tarFile, File outputDirectory) throws IOException {
		if (!outputDirectory.exists()) outputDirectory.mkdirs();
		try (TarArchiveInputStream tarInput = new TarArchiveInputStream(tarFile)) {
			processEntry(tarInput, outputDirectory);
		}
	}

	private static void processEntry(TarArchiveInputStream tarInput, File outputDirectory) throws IOException {
		TarArchiveEntry entry;
		while ((entry = tarInput.getNextTarEntry()) != null) {
			File outputFile = new File(outputDirectory, entry.getName());
			if (outputFile.getName().startsWith(".")) continue;
			if (entry.isDirectory()) outputFile.mkdirs();
			else writeFile(outputFile, tarInput);
		}
	}

	private static void writeFile(File outputFile, TarArchiveInputStream tarInput) throws IOException {
		File parent = outputFile.getParentFile();
		if (parent != null && !parent.exists()) parent.mkdirs();
		try (OutputStream outputStream = new FileOutputStream(outputFile)) {
			byte[] buffer = new byte[1024];
			int bytesRead;
			while ((bytesRead = tarInput.read(buffer)) != -1) {
				outputStream.write(buffer, 0, bytesRead);
			}
		}
	}
}