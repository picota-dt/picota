package io.picota.runtime;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import java.io.*;

public class Tar {

	public static void createTarFile(File sourceDir, File tarFile) throws IOException {
		try (FileOutputStream fos = new FileOutputStream(tarFile);
			 TarArchiveOutputStream tos = new TarArchiveOutputStream(fos)) {
			addFilesToTar(sourceDir, sourceDir, tos);
		}
	}

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

	private static void addFilesToTar(File rootDir, File currentFile, TarArchiveOutputStream tos) throws IOException {
		String entryName = rootDir.toURI().relativize(currentFile.toURI()).getPath();
		TarArchiveEntry entry = new TarArchiveEntry(currentFile, entryName);
		if (currentFile.isFile()) {
			entry.setSize(currentFile.length());
			tos.putArchiveEntry(entry);

			try (FileInputStream fis = new FileInputStream(currentFile)) {
				byte[] buffer = new byte[1024];
				int length;
				while ((length = fis.read(buffer)) > 0) {
					tos.write(buffer, 0, length);
				}
			}
			tos.closeArchiveEntry();
		} else if (currentFile.isDirectory()) {
			tos.putArchiveEntry(entry);
			tos.closeArchiveEntry();
			File[] children = currentFile.listFiles();
			if (children != null) {
				for (File child : children) {
					addFilesToTar(rootDir, child, tos);
				}
			}
		}
	}
}