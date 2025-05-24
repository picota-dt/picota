package io.picota.digitalmodel.builder;

import io.intino.alexandria.Resource;
import io.intino.alexandria.logger.Logger;
import io.picota.digitalmodel.DigitalModelBox.State;
import io.picota.digitalmodel.ModelLoader;
import io.picota.digitalmodel.builder.DigitalSubjectBuilder.Result.Training;
import io.picota.digitalmodel.setup.RuntimeCodeGenerator;
import io.picota.digitalmodel.utils.Compression;
import model.DigitalTwin;
import model.PicotaGraph;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

public class DigitalSubjectBuilder {
	private final File workingDir;
	private final Map<DigitalTwin, State> states;
	private final File pythonVenv;
	private final ExecutorService executor;
	private Future<?> current;
	private String status = "Initializing...";

	public DigitalSubjectBuilder(File workingDir, Map<DigitalTwin, State> states, File pythonVenv) {
		this.workingDir = workingDir;
		this.states = states;
		this.pythonVenv = pythonVenv;
		this.executor = createExecutor();
	}

	public Future<?> build(String modelUrl, Resource zipData, OnFinished onFinished) {
		if (current != null && !current.isDone())
			throw new IllegalStateException(this.status = "Last execution is not terminated");
		current = this.executor.submit(() -> {
			try {
				buildDt(modelUrl, zipData, onFinished);
			} catch (Throwable e) {
				Logger.error(e);
			}
		});
		return current;
	}

	private void buildDt(String url, Resource resource, OnFinished onFinished) throws IOException, InterruptedException, URISyntaxException {
		PicotaGraph graph = buildModel(url);
		if (graph == null) throw new IllegalStateException(this.status = "Impossible to load model");
		File dtDirectory = new File(workingDir, modelName(new URI(url)));
		File datasets = downloadDataset(resource, dtDirectory);
		new RuntimeCodeGenerator(dtDirectory, graph).generate();
		for (DigitalTwin dt : graph.digitalTwinList())
			processDigitalTwin(dt, dtDirectory, findFile(datasets, dt.name$()), onFinished);
		clean(dtDirectory);
	}

	private void processDigitalTwin(DigitalTwin dt, File dtDirectory, File dataset, OnFinished onFinished) throws IOException, InterruptedException {
		states.put(dt, State.Training);
		if (!dataset.exists() || dataset.length() == 0) {
			states.put(dt, State.WaitingData);
			return;
		}
		File temp = new File(dtDirectory, "temp");
		File data = new File(dtDirectory, "data");
		new DataPreparer(temp, data).prepareData(dt, dataset);
		onFinished.onFinished(train(dt, dtDirectory));
		states.put(dt, State.Prepared);
	}

	private String modelName(URI uri) {
		Path path = Path.of(uri);
		return path.getName(path.getNameCount() - 1).toString().replace(".zip", "");
	}

	private void clean(File dtDirectory) {
		try {
			FileUtils.deleteDirectory(new File(dtDirectory, "temp"));
			FileUtils.deleteDirectory(new File(dtDirectory, "data"));
			FileUtils.deleteDirectory(new File(dtDirectory, "scripts"));
		} catch (IOException e) {
			Logger.error(e);
		}
	}

	private File findFile(File datasets, String name) {
		File file = new File(datasets, name + ".csv");
		return file.exists() ? file : new File(datasets, name + ".tsv");
	}

	@NotNull
	private File downloadDataset(Resource dataset, File dtDirectory) throws IOException {
		File temp = new File(dtDirectory, "temp");
		temp.mkdirs();
		File zipFile = new File(temp, dataset.name());
		Files.copy(dataset.inputStream(), zipFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		Compression.unzip(zipFile, temp);
		zipFile.delete();
		return temp;
	}

	private PicotaGraph buildModel(String url) {
		try {
			return new ModelLoader().load(new URI(url).toURL());
		} catch (URISyntaxException | IOException e) {
			Logger.error(e);
			return null;
		}
	}

	private Result train(DigitalTwin dt, File dtDirectory) throws IOException, InterruptedException {
		Logger.info(this.status = "Training " + dt.name$() + "...");
		Result result = runTrain(dtDirectory);
		Logger.info(this.status = "Finished training of " + dt.name$() + ". Code: " + result.statusCode);
		return result;
	}

	private Result runTrain(File dtDirectory) throws IOException, InterruptedException {
		String pythonExecutable = pythonVenv.getAbsolutePath() + "/bin/python";
		File scripts = new File(dtDirectory, "scripts");
		File modelsDir = new File(dtDirectory, "models");
		modelsDir.mkdirs();
		File scriptPath = new File(dtDirectory, "scripts/trainer/main.py");
		if (!scriptPath.exists()) throw new IOException("Main script not found: " + scriptPath.getAbsolutePath());
		Process process = new ProcessBuilder(pythonExecutable, scriptPath.getAbsolutePath(), new File(dtDirectory, "data").getAbsolutePath(), modelsDir.getAbsolutePath())
				.redirectErrorStream(true)
				.directory(scripts)
				.start();
		int code = process.waitFor();
		String report = new String(process.getInputStream().readAllBytes());
		return new Result(dtDirectory.getName(), code, report, trainings(code, report));
	}

	public boolean isRunning() {
		return !this.executor.isTerminated();
	}

	public void stop() {
		if (current != null) current.cancel(true);
	}

	public String status() {
		return status;
	}

	public record Result(String model, int statusCode, String report, List<Training> trainings) {
		public record Training(String dt, String variable, double loss, String[] contributors) {
		}
	}

	private List<Training> trainings(int code, String report) {
		return code != 0 ? List.of() : report.lines().map(l -> trainingResultOf(l.split("\t"))).toList();
	}

	private static ExecutorService createExecutor() {
		return Executors.newSingleThreadExecutor(new ThreadFactory() {
			int counter = 0;

			@Override
			public Thread newThread(Runnable r) {
				Thread thread = new Thread(r, "Trainer-" + counter++);
				thread.setDaemon(true);
				return thread;
			}
		});
	}

	private Training trainingResultOf(String[] fields) {
		return new Training(fields[0], fields[1], Double.parseDouble(fields[2]), Arrays.stream(fields).skip(3).toArray(String[]::new));
	}

	public interface OnFinished {
		void onFinished(Result result);
	}
}