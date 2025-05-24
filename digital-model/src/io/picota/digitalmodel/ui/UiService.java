package io.picota.digitalmodel.ui;

import com.google.gson.Gson;
import io.intino.alexandria.Resource;
import io.intino.alexandria.http.AlexandriaHttpServer;
import io.intino.alexandria.http.AlexandriaHttpServerBuilder;
import io.intino.alexandria.http.server.AlexandriaHttpManager;
import io.intino.alexandria.logger.Logger;
import io.picota.digitalmodel.DigitalModelBox;
import io.picota.digitalmodel.TrainReportBuilder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class UiService {
	private final DigitalModelBox box;
	private static final ExecutorService TRAINING_POOL = Executors.newCachedThreadPool();
	private volatile boolean trainingDone = false;
	private Future<?> buildTask;

	public UiService(DigitalModelBox box) {
		this.box = box;
	}

	public void start() {
		AlexandriaHttpServer<?> server = AlexandriaHttpServerBuilder.instance();
		server.route("/").get(manager -> {
			manager.response().header("Content-Type", "text/html");
			manager.write(page("/index.html"));
		});
		server.route("/wizard").get(manager -> {
			manager.response().header("Content-Type", "text/html");
			manager.write(page("/wizard.html"));
		});
		server.route("/results/report.pdf").get(UiService::loadReport);
		server.route("/results/digital-twin.zip").get(UiService::loadDigitalTwin);
		server.route("/train").post(ctx -> {
			if (box.dtBuilder().isRunning()) ctx.response().error(400, "Already training");
			trainingDone = false;
			train(ctx.fromFormAsString("modelUrl"), ctx.fromFormAsResource("dataset"));
			ctx.write(new Gson().toJson(Map.of("status", "ok")));
		});
		server.route("/status").get(ctx -> ctx.write(new Gson().toJson(Map.of("status", buildTask.isDone() ? "done" : "processing"))));
	}

	private void train(String modelUrl, Resource dataset) {
		try {
			File datasetFile = Files.createTempFile(dataset.name(), ".zip").toFile();
			Files.write(datasetFile.toPath(), dataset.readAllBytes());
			buildTask = box.dtBuilder().build(modelUrl, datasetFile, result -> {
				TrainReportBuilder trainReportBuilder = new TrainReportBuilder(result.trainings(), result.report());
				trainReportBuilder.save(new File(box.configuration().home(), "report.pdf"));
			});
		} catch (Exception e) {
			Logger.error(e);
		}
	}

	private static void loadDigitalTwin(AlexandriaHttpManager<?> ctx) {
		ctx.writeHeader("Content-Type", "application/zip");
		ctx.writeHeader("Content-Disposition", "attachment; filename=\"digital-twin.zip\"");
		try (InputStream is = Main.class.getResourceAsStream("/digital-twin.zip")) {
			if (is == null) ctx.response().error(404, "digital-twin not found");
			else ctx.write(is);
		} catch (IOException e) {
			Logger.error(e);
		}
	}

	private static void loadReport(AlexandriaHttpManager<?> ctx) {
		ctx.writeHeader("Content-Type", "application/pdf");
		ctx.writeHeader("Content-Disposition", "attachment; filename=\"report.pdf\"");
		try (InputStream is = Main.class.getResourceAsStream("/downloads/reporte.pdf")) {
			if (is == null) ctx.response().error(404, "pdf not found");
			else ctx.write(is);
		} catch (IOException e) {
			Logger.error(e);
		}
	}

	private static String page(String page) {
		try (InputStream is = Main.class.getResourceAsStream(page)) {
			if (is == null) throw new FileNotFoundException("Resource not found: " + page);
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			return "Error reading file: " + page;
		}
	}
}
