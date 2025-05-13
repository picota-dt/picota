package io.picota.digitalmodel;

import io.intino.alexandria.logger.Logger;
import io.intino.alexandria.zip.Zip;
import io.intino.magritte.builder.StashBuilder;
import io.intino.magritte.framework.stores.FileSystemStore;
import io.intino.magritte.io.model.Stash;
import io.picota.language.model.PicotaGraph;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Level;
import tara.dsl.Picota;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static io.intino.alexandria.logger4j.Logger.*;

public class Main {
	public static void main(String[] args) throws IOException, URISyntaxException {
		setLevel(Level.ERROR);
		run(args);
	}

	public static DigitalModelBox run(String[] args) throws MalformedURLException, URISyntaxException {
		var configuration = new DigitalModelConfiguration(args);
		var workingDir = new File(configuration.home(), "picota");
		PicotaGraph graph = loadModel(new URI(configuration.modelUrl()).toURL(), workingDir);
		DigitalModelBox box = new DigitalModelBox(configuration, graph, workingDir);
		box.start();
		Runtime.getRuntime().addShutdownHook(new Thread(box::stop));
		return box;
	}

	private static PicotaGraph loadModel(URL url, File home) {
		File project = unzipProject(url, home);
		Stash[] build = new StashBuilder(taraFiles(project), new Picota(), "dsl", System.out).build();
		if (build.length == 0) return null;
		FileUtils.deleteQuietly(project);
		return PicotaGraph.load(new FileSystemStore(home), build);
	}

	private static File unzipProject(URL url, File home) {
		try {
			byte[] bytes = url.openStream().readAllBytes();
			Path zip = Files.createTempFile("_picota", ".zip");
			File sources = new File(home, "project");
			sources.mkdirs();
			Files.write(zip, bytes);
			new Zip(zip.toFile()).unzip(sources.getAbsolutePath());
			cleanSources(sources);
			return sources;
		} catch (IOException e) {
			Logger.error(e);
		}
		return home;
	}

	private static void cleanSources(File sources) throws IOException {
		FileUtils.deleteDirectory(new File(sources, "__MACOSX"));
		Arrays.stream(Objects.requireNonNull(sources.listFiles())).filter(f -> f.isFile() && f.getName().startsWith(".")).forEach(File::delete);
	}

	private static Map<File, Charset> taraFiles(File project) {
		return FileUtils.listFiles(project, new String[]{"tara"}, true).stream().collect(Collectors.toMap(f -> f, f -> StandardCharsets.UTF_8));
	}
}