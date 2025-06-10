package io.picota.digitaltwin.control.commands;

import io.intino.alexandria.Resource;
import io.picota.digitaltwin.DigitalTwinBox;
import io.picota.digitaltwin.control.utils.Compression;
import io.picota.digitaltwin.model.DigitalTwin;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class DownloadDataCommand implements Command<Void> {
	private final DigitalTwinBox box;
	private final String id;
	private final Resource data;

	public DownloadDataCommand(DigitalTwinBox box, String id, Resource data) {
		this.box = box;
		this.id = id;
		this.data = data;
	}

	@Override
	public Result<Void> execute() {
		try {
			DigitalTwin digitalTwin = box.store().get(id);
			if (digitalTwin == null) throw new IllegalArgumentException("DigitalTwin not found");
			downloadDataset(digitalTwin, data);
			digitalTwin.state(DigitalTwin.State.DownloadedData);
			return Command.success();
		} catch (IOException e) {
			return new Result(false, e.getMessage());
		}
	}

	private void downloadDataset(DigitalTwin digitalTwin, Resource dataset) throws IOException {
		File directory = digitalTwin.archetype().rawDataDirectory();
		FileUtils.deleteDirectory(directory);
		directory.mkdirs();
		File zipFile = new File(directory, dataset.name());
		Files.copy(dataset.inputStream(), zipFile.toPath(), REPLACE_EXISTING);
		Compression.unzip(zipFile, directory);
		zipFile.delete();
		cleanMetaDataFiles(directory);
	}

	private void cleanMetaDataFiles(File directory) {
		for (File file : Objects.requireNonNull(directory.listFiles((d, n) -> n.startsWith(".") || (n.startsWith("__") && new File(directory, n).isDirectory())))) {
			if (file.isFile()) file.delete();
			else {
				try {
					FileUtils.deleteDirectory(file);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}
	}
}
