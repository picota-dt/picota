package io.picota.digitaltwin.control.commands;

import io.intino.alexandria.Resource;
import io.picota.digitaltwin.DigitalTwinBox;

import java.util.HashMap;
import java.util.Map;

public class CommandFactory {

	private final DigitalTwinBox box;

	public CommandFactory(DigitalTwinBox box) {
		this.box = box;
	}

	static Map<Class<? extends Command>, CommandBuilder> commands = new HashMap<>();

	static {
		commands.put(BuildModelCommand.class, CommandFactory::buildModel);
		commands.put(DownloadDataCommand.class, CommandFactory::downloadData);
		commands.put(EvaluateVariablesCommand.class, CommandFactory::evaluateVariables);
		commands.put(ReadModelCommand.class, CommandFactory::readModel);
		commands.put(ReadDigitalTwinCommand.class, CommandFactory::readDigitalTwin);
		commands.put(ProvideReportCommand.class, CommandFactory::provideReport);
		commands.put(TrainSubjectsCommand.class, CommandFactory::trainVariables);
		commands.put(CsvTemplateCommand.class, CommandFactory::csvTemplate);
	}

	public <T extends Command> T build(Class<T> command, Object... args) {
		CommandBuilder commandBuilder = commands.get(command);
		if (commandBuilder == null) throw new IllegalArgumentException("Unknown command: " + command);
		return (T) commandBuilder.build(box, args);
	}

	private static ReadModelCommand readModel(DigitalTwinBox b, Object... args) {
		return new ReadModelCommand(b, (String) args[0]);
	}

	private static ReadDigitalTwinCommand readDigitalTwin(DigitalTwinBox digitalTwinBox, Object[] objects) {
		return new ReadDigitalTwinCommand(digitalTwinBox, (String) objects[0]);
	}

	private static DownloadDataCommand downloadData(DigitalTwinBox b, Object... args) {
		return new DownloadDataCommand(b, (String) args[0], (Resource) args[1]);
	}

	private static BuildModelCommand buildModel(DigitalTwinBox b, Object... args) {
		return new BuildModelCommand(b, (String) args[0], (Resource) args[1]);
	}

	private static TrainSubjectsCommand trainVariables(DigitalTwinBox b, Object... args) {
		return new TrainSubjectsCommand(b, (String) args[0]);
	}

	private static Command csvTemplate(DigitalTwinBox digitalTwinBox, Object... objects) {
		return new CsvTemplateCommand(digitalTwinBox, (String) objects[0]);
	}

	private static ProvideReportCommand provideReport(DigitalTwinBox b, Object... args) {
		return new ProvideReportCommand(b, (String) args[0]);
	}

	private static EvaluateVariablesCommand evaluateVariables(DigitalTwinBox b, Object... args) {
		return new EvaluateVariablesCommand(b, (String) args[0], (Map<String, Object>) args[1]);
	}

	public interface CommandBuilder {
		Command build(DigitalTwinBox box, Object... args);
	}
}
