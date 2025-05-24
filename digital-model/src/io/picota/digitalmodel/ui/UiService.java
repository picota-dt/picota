package io.picota.digitalmodel.ui;

import com.google.gson.Gson;
import io.intino.alexandria.Resource;
import io.intino.alexandria.http.AlexandriaHttpServer;
import io.intino.alexandria.http.AlexandriaHttpServerBuilder;
import io.intino.alexandria.http.server.AlexandriaHttpManager;
import io.intino.alexandria.logger.Logger;
import io.picota.digitalmodel.DigitalModelBox;
import io.picota.digitalmodel.builder.TrainReportBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
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
		server.route("/status").get(this::sendStatus);
		server.route("/results/{model}/report.pdf").get(this::loadReport);
		server.route("/results/{model}/models.zip").get(this::loadDigitalTwin);
	}

	private void sendStatus(AlexandriaHttpManager<?> ctx) {
		ctx.write(new Gson().toJson(Map.of(
				"status", buildTask == null || buildTask.isDone() ? "done" : "processing",
				"message", box.dtBuilder().status())));
	}

	private static void html(AlexandriaHttpManager<?> manager, String page) {
		manager.response().header("Content-Type", "text/html");
		manager.write(page);
	}

	private void train(AlexandriaHttpManager<?> ctx) {
		if (box.dtBuilder().isRunning()) ctx.response().error(400, "Already training");
		train(ctx.fromFormAsString("modelUrl"), ctx.fromFormAsResource("dataset"));
		ctx.response().status(200);
		ctx.write(new Gson().toJson(Map.of("status", "ok")));
	}

	private void train(String modelUrl, Resource dataset) {
		try {
			buildTask = box.dtBuilder().build(modelUrl, dataset, result -> {
				try {
					new TrainReportBuilder().generate(result, new File(box.workingDir(), result.model() + "/report.pdf"));
				} catch (IOException e) {
					Logger.error(e);
				}
			});
		} catch (Exception e) {
			Logger.error(e);
		}
	}

	private void loadDigitalTwin(AlexandriaHttpManager<?> ctx) {
		ctx.writeHeader("Content-Type", "application/zip");
		ctx.writeHeader("Content-Disposition", "attachment; filename=\"digital-models.zip\"");
		File dtDir = new File(box.workingDir(), ctx.fromPath("model"));
		File file = new File(dtDir, "models/models.zip");
		if (!file.exists()) ctx.response().error(404, "digital models not found");
		else try (InputStream is = new FileInputStream(file)) {
			ctx.write(is);
		} catch (IOException e) {
			Logger.error(e);
		}
	}

	private void loadReport(AlexandriaHttpManager<?> ctx) {
		ctx.writeHeader("Content-Type", "application/pdf");
		ctx.writeHeader("Content-Disposition", "attachment; filename=\"report.pdf\"");
		File dtDir = new File(box.workingDir(), ctx.fromPath("model"));
		File file = new File(dtDir, "report.pdf");
		if (!file.exists()) ctx.response().error(404, "Report not found");
		ctx.write(new Resource("report.pdf", file));
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
