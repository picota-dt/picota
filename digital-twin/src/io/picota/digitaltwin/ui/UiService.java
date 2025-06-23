package io.picota.digitaltwin.ui;

import com.google.gson.Gson;
import io.intino.alexandria.Resource;
import io.intino.alexandria.http.AlexandriaHttpServer;
import io.intino.alexandria.http.AlexandriaHttpServerBuilder;
import io.intino.alexandria.http.server.AlexandriaHttpManager;
import io.intino.alexandria.logger.Logger;
import io.javalin.http.NotFoundResponse;
import io.picota.digitaltwin.control.DigitalTwinsStore;
import io.picota.digitaltwin.control.commands.*;
import io.picota.digitaltwin.control.commands.Command.Result;
import io.picota.digitaltwin.control.utils.Utils;
import io.picota.digitaltwin.model.DigitalTwin;
import io.quassar.monentia.picota.DigitalTwin.DigitalSubject;
import io.quassar.monentia.picota.Variable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
		server.route("/wizard/{id}").get(manager -> customHtml(manager, page("/www/wizard.html")));
		server.route("/wizard/{id}/template").get(this::csvTemplate);
		server.route("/digital-twin/{id}").get(manager -> digitalTwinHtml(manager, page("/www/digital-twin.html")));
		server.route("/digital-twin/{id}/info").get(this::getDigitalTwin);
		server.route("/digital-twin").post(this::postDigitalTwin);
		server.route("/digital-twin/{id}/state/{state}").post(this::postState);
		server.route("/digital-twin/{id}/report").get(this::loadReport);
		server.route("/evaluation").get(manager -> customHtml(manager, page("/www/evaluation.html")));
	}

	private void getDigitalTwin(AlexandriaHttpManager<?> ctx) {
		String id = ctx.fromPath("id");
		ctx.response().header("Content-Type", "application/json");
		ReadDigitalTwinCommand command = factory.build(ReadDigitalTwinCommand.class, id);
		Result<Map<String, ? extends Serializable>> result = command.execute();
		if (result.success()) {
			ctx.response().status(200);
			ctx.write(new Gson().toJson(result.resource()));
		} else {
			ctx.response().status(404);
			ctx.write(new Gson().toJson(Map.of("status", "FAILURE", "message", result.remarks().substring(0, Math.min(result.remarks().length(), 100)))));
		}

	}

	private void postDigitalTwin(AlexandriaHttpManager<?> ctx) {
		String modelUrl = ctx.fromFormAsString("modelUrl");
		ctx.response().header("Content-Type", "application/json");
		ReadModelCommand command = factory.build(ReadModelCommand.class, modelUrl);
		Result<DigitalTwin> result = command.execute();
		if (result.success()) {
			ctx.response().status(200);
			ctx.write(new Gson().toJson(Map.of("status", "SUCCESS", "state", result.resource().state().name())));
		} else {
			ctx.response().status(404);
			ctx.write(new Gson().toJson(Map.of("status", "FAILURE", "message", result.remarks().substring(0, Math.min(result.remarks().length(), 100)))));
		}
	}

	private void digitalTwinHtml(AlexandriaHttpManager<?> ctx, String page) {
		ctx.response().header("Content-Type", "text/html");
		String id = ctx.fromPath("id");
		if (id == null) error(ctx, 404, new Result<>(false, ""));
		else {
			DigitalTwin digitalTwin = store.get(id);
			if (digitalTwin == null || digitalTwin.graph() == null) error(ctx, 404, new Result<>(false, ""));
			else {
				page = page.replace("$id", digitalTwin.id()).replace("$name", digitalTwin.name()).replace("$version", digitalTwin.version());
				if (digitalTwin.report() != null) page = page.replace("$validationLoss", validationLoss(digitalTwin))
						.replace("$feature", contributor(digitalTwin));
				else
					page = page.replace("$validationLoss", "-").replace("$feature", "-");
				DigitalSubject ds = digitalTwin.graph().digitalTwin().digitalSubjectList().getFirst();
				page = page.replace("$subject", ds.subject().name$() + (ds.subject().isPrototype() ? "001" : ""))
						.replace("$variables", String.join(",\n\t\t\t\t\t\t\t", variablesOf(ds)));
			}
			ctx.write(page);
		}
	}

	private static List<String> variablesOf(DigitalSubject ds) {
		List<String> variables = new ArrayList<>();
		Utils.variableTypes(ds.subject()).entrySet().stream()
				.map(e -> "\"" + e.getKey() + "\"" + ": " + defaultValue(e.getValue()))
				.forEach(variables::add);
		return variables.stream().sorted().collect(Collectors.toList());
	}

	private static String defaultValue(Variable variable) {
		if (variable.isNumeric()) return "0";
		if (variable.isEnumerated()) return "\"" + variable.asEnumerated().values().getFirst() + "\"";
		if (variable.isBoolean()) return "false";
		return "0";
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

	private void postState(AlexandriaHttpManager<?> ctx) {
		try {
			Resource dataset = ctx.fromFormAsResource("dataset");
			String id = ctx.fromPath("id");
			String notifyEmail = ctx.fromFormAsString("notifyEmail");
			Result<Void> result = factory.build(BuildModelCommand.class, id, notifyEmail, dataset).execute();
			if (result.success()) success(ctx, result);
			else error(ctx, 400, result);
		} catch (IllegalArgumentException e) {
			ctx.response().status(400);
			ctx.write(new Gson().toJson(Map.of("status", "FAILED")));
		} catch (Exception e) {
			Logger.error(e);
			ctx.response().status(500);
			ctx.write(new Gson().toJson(Map.of("status", "FAILED")));
		}
	}

	private static void success(AlexandriaHttpManager<?> ctx, Result<?> result) {
		ctx.response().status(200);
		ctx.write(new Gson().toJson(Map.of("status", "SUCCESS", "message", result.remarks())));
	}

	private static void error(AlexandriaHttpManager<?> ctx, int code, Result<?> result) {
		ctx.response().status(code);
		ctx.write(new Gson().toJson(Map.of("status", "FAILED", "message", result.remarks())));
	}

	private void csvTemplate(AlexandriaHttpManager<?> ctx) {
		ctx.writeHeader("Content-Type", "application/zip");
		ctx.writeHeader("Content-Disposition", "attachment; filename=\"template.zip\"");
		String dtId = ctx.fromPath("id");
		Result<File> result = factory.build(CsvTemplateCommand.class, dtId).execute();
		if (!result.success()) ctx.response().error(404, "Report not found");
		ctx.write(new Resource("templates.zip", result.resource()));
	}

	private void loadReport(AlexandriaHttpManager<?> ctx) {
		ctx.writeHeader("Content-Type", "application/pdf");
		ctx.writeHeader("Content-Disposition", "attachment; filename=\"report.pdf\"");
		String dtId = ctx.fromPath("id");
		Result<File> result = factory.build(ProvideReportCommand.class, dtId).execute();
		if (!result.success()) ctx.response().error(404, "Report not found");
		File resource = result.resource();
		if (resource != null && resource.exists())
			ctx.write(new Resource("report.pdf", resource));
		else throw new NotFoundResponse("Report not found");
	}

	private void customHtml(AlexandriaHttpManager<?> manager, String page) {
		manager.response().header("Content-Type", "text/html");
		String modelId = manager.fromQueryOrDefault("id", "");
		if (modelId == null || modelId.isEmpty()) modelId = manager.fromPathOrDefault("id", "");
		if (modelId == null) modelId = "";
		page = page.replace("$modelId", modelId);
		int step = calculateStep(modelId);
		page = page.replace("$step", step + "");
		manager.write(page);
	}

	private int calculateStep(String id) {
		DigitalTwin digitalTwin = store.get(id);
		return digitalTwin != null && (digitalTwin.state() == DigitalTwin.State.PreparingData || digitalTwin.state() == DigitalTwin.State.Training) ?
				3 : 0;
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
