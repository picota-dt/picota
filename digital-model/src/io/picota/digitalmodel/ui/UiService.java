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
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.Future;

public class UiService {
	private final DigitalModelBox box;
	private Future<?> buildTask;

	public UiService(DigitalModelBox box) {
		this.box = box;
	}

	public void start() {
		AlexandriaHttpServer<?> server = AlexandriaHttpServerBuilder.instance();
		server.route("/").get(manager -> html(manager, page("/index.html")));
		server.route("/wizard").get(manager -> html(manager, page("/wizard.html")));
		server.route("/help").get(manager -> html(manager, page("/help.html")));
		server.route("/train").post(this::train);
		server.route("/status").get(ctx -> ctx.write(new Gson().toJson(Map.of("status", buildTask.isDone() ? "done" : "processing"))));
		server.route("/results/report.pdf").get(UiService::loadReport);
		server.route("/results/digital-twin.zip").get(UiService::loadDigitalTwin);
	}

	private static AlexandriaHttpManager<?> html(AlexandriaHttpManager<?> manager, String page) {
		manager.response().header("Content-Type", "text/html");
		manager.write(page);
		return manager;
	}

	private void train(AlexandriaHttpManager<?> ctx) {
		if (box.dtBuilder().isRunning()) ctx.response().error(400, "Already training");
		train(ctx.fromFormAsString("modelUrl"), ctx.fromFormAsResource("dataset"));
		ctx.response().status(200);
		ctx.write(new Gson().toJson(Map.of("status", "ok")));

	}

	private void train(String modelUrl, Resource dataset) {
		try {
			File datasetFile = new File(box.configuration().home(), dataset.name());
			Files.copy(dataset.inputStream(), datasetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			datasetFile.deleteOnExit();
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
		try (InputStream is = UiService.class.getResourceAsStream("/digital-twin.zip")) {
			if (is == null) ctx.response().error(404, "digital-twin not found");
			else ctx.write(is);
		} catch (IOException e) {
			Logger.error(e);
		}
	}

	private static void loadReport(AlexandriaHttpManager<?> ctx) {
		ctx.writeHeader("Content-Type", "application/pdf");
		ctx.writeHeader("Content-Disposition", "attachment; filename=\"report.pdf\"");
		try (InputStream is = UiService.class.getResourceAsStream("/downloads/reporte.pdf")) {
			if (is == null) ctx.response().error(404, "pdf not found");
			else ctx.write(is);
		} catch (IOException e) {
			Logger.error(e);
		}
	}

	private static String page(String page) {
		try (InputStream is = UiService.class.getResourceAsStream(page)) {
			if (is == null) throw new FileNotFoundException("Resource not found: " + page);
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			return "Error reading file: " + page;
		}
	}
}
