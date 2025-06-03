package io.picota.digitaltwin.ui;

import com.google.gson.Gson;
import io.intino.alexandria.Resource;
import io.intino.alexandria.http.AlexandriaHttpServer;
import io.intino.alexandria.http.AlexandriaHttpServerBuilder;
import io.intino.alexandria.http.server.AlexandriaHttpManager;
import io.intino.alexandria.logger.Logger;
import io.picota.digitaltwin.control.DigitalTwinsStore;
import io.picota.digitaltwin.control.commands.*;
import io.picota.digitaltwin.model.DigitalTwin;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class UiService {
	private final DigitalTwinsStore store;
	private final CommandFactory factory;

	public UiService(DigitalTwinsStore store, CommandFactory factory) {
		this.store = store;
		this.factory = factory;
	}

	public void start() {
		AlexandriaHttpServer<?> server = AlexandriaHttpServerBuilder.instance();
		server.route("/").get(manager -> html(manager, page("/www/index.html")));
		server.route("/wizard").get(manager -> customHtml(manager, page("/www/wizard.html")));
		server.route("/digital-twin/{id}").get(manager -> digitalTwinHtml(manager, page("/www/digital-twin.html")));
		server.route("/digital-twin/{id}/info").get(this::getDigitalTwin);
		server.route("/digital-twin").post(this::postDigitalTwin);
		server.route("/digital-twin/{id}/state/{state}").post(this::postState);
		server.route("/digital-twin/{id}/evaluation").get(this::evaluation);
		server.route("/digital-twin/{id}/report").get(this::loadReport);
		server.route("/evaluation").get(manager -> customHtml(manager, page("/www/evaluation.html")));
	}

	private void getDigitalTwin(AlexandriaHttpManager<?> manager) {
		String id = manager.fromPath("id");
		manager.response().header("Content-Type", "application/json");
		Command command = factory.build(ReadDigitalTwinCommand.class, id);
		Command.Result result = command.execute();
		if (result.success()) {
			manager.response().status(200);
			manager.write(new Gson().toJson(result.resource()));
		} else {
			manager.response().status(404);
			manager.write(new Gson().toJson(Map.of("status", "FAILURE", "message", result.remarks().substring(0, Math.min(result.remarks().length(), 100)))));
		}

	}

	private void postDigitalTwin(AlexandriaHttpManager<?> manager) {
		String modelUrl = manager.fromFormAsString("modelUrl");
		manager.response().header("Content-Type", "application/json");
		Command command = factory.build(ReadModelCommand.class, modelUrl);
		Command.Result result = command.execute();
		if (result.success()) {
			DigitalTwin resource = (DigitalTwin) result.resource();
			manager.response().status(200);
			manager.write(new Gson().toJson(Map.of("status", "SUCCESS", "state", resource.state().name())));
		} else {
			manager.response().status(404);
			manager.write(new Gson().toJson(Map.of("status", "FAILURE", "message", result.remarks().substring(0, Math.min(result.remarks().length(), 100)))));
		}
	}

	private void digitalTwinHtml(AlexandriaHttpManager<?> manager, String page) {
		manager.response().header("Content-Type", "text/html");
		String id = manager.fromPath("id");
		if (id == null) error(manager, 404, new Command.Result(false, ""));
		else {
			DigitalTwin digitalTwin = store.get(id);
			if (digitalTwin == null) error(manager, 404, new Command.Result(false, ""));
			else {
				page = page.replace("$id", digitalTwin.id()).replace("$name", digitalTwin.name()).replace("$version", digitalTwin.version());
				if (digitalTwin.report() != null) page = page.replace("$validationLoss", validationLoss(digitalTwin))
						.replace("$feature", contributor(digitalTwin));
				else
					page = page.replace("$validationLoss", "-").replace("$feature", "-");
			}
			manager.write(page);
		}
	}

	private String contributor(DigitalTwin digitalTwin) {
		Map<String, Long> counts = digitalTwin.report().trainings().stream().map(v -> v.contributors().entrySet().stream()
				.sorted(Map.Entry.comparingByValue(Comparator.reverseOrder())).iterator().next().getKey()).collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
		return counts.entrySet().stream()
				.max(Map.Entry.comparingByValue())
				.map(Map.Entry::getKey).orElse("-");

	}

	private static String validationLoss(DigitalTwin digitalTwin) {
		return String.format("%.2f", digitalTwin.report().trainings().stream().mapToDouble(DigitalTwin.TrainingReport.Variable::loss).average().orElse(0));
	}

	private void evaluation(AlexandriaHttpManager<?> o) {

	}

	private void postState(AlexandriaHttpManager<?> ctx) {
		try {
			Resource dataset = ctx.fromFormAsResource("dataset");
			String id = ctx.fromPath("id");
			Command.Result result = factory.build(BuildModelCommand.class, id, dataset).execute();
			if (result.success()) success(ctx, result);
			else error(ctx, 400, result);
		} catch (Exception e) {
			Logger.error(e);
			ctx.response().status(500);
			ctx.write(new Gson().toJson(Map.of("status", "FAILED")));
		}
	}

	private static void success(AlexandriaHttpManager<?> ctx, Command.Result result) {
		ctx.response().status(200);
		ctx.write(new Gson().toJson(Map.of("status", "SUCCESS", "message", result.remarks())));
	}

	private static void error(AlexandriaHttpManager<?> ctx, int code, Command.Result result) {
		ctx.response().status(code);
		ctx.write(new Gson().toJson(Map.of("status", "FAILED", "message", result.remarks())));
	}

	private void loadReport(AlexandriaHttpManager<?> ctx) {
		ctx.writeHeader("Content-Type", "application/pdf");
		ctx.writeHeader("Content-Disposition", "attachment; filename=\"report.pdf\"");
		String dtId = ctx.fromPath("id");
		Command.Result result = factory.build(ProvideReportCommand.class, dtId).execute();
		if (!result.success()) ctx.response().error(404, "Report not found");
		ctx.write(new Resource("report.pdf", (File) result.resource()));
	}

	private static void customHtml(AlexandriaHttpManager<?> manager, String page) {
		manager.response().header("Content-Type", "text/html");
		String modelId = manager.fromQueryOrDefault("id", "");
		if (modelId != null) page = page.replace("$modelId", modelId);
		manager.write(page);
	}

	private static void html(AlexandriaHttpManager<?> manager, String page) {
		manager.response().header("Content-Type", "text/html");
		manager.write(page);
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
