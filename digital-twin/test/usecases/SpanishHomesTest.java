package usecases;

import io.intino.alexandria.logger.Logger;
import io.picota.digitaltwin.DigitalTwinBox;
import io.picota.digitaltwin.DigitalTwinConfiguration;
import io.picota.digitaltwin.control.commands.Command;
import io.picota.digitaltwin.control.commands.CommandFactory;
import io.picota.digitaltwin.control.commands.ReadModelCommand;
import io.picota.digitaltwin.control.commands.trainvariablescommand.TrainDataPreparer;
import io.quassar.monentia.picota.DigitalTwin;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class SpanishHomesTest {
	private static final String BASE_PATH = "../runtime.test/data";
	private static final String ID = "b9ed413b-68c7-487f-bea8-81887a538521";
	private static final String ID_ENGLISH = "dba3b0e0-f1bc-42a6-8e5e-dae36f07c5b1";
	private static final File SUBJECT_DATASETS = new File(BASE_PATH + "/spanish_homes");
	private static final String URL = "https://quassar.io/commits/" + ID;
	private CommandFactory factory;


	@Before
	public void setUp() {
		String[] args = {"python_venv=/Users/oroncal/workspace/projects/picota-smartbeach/.venv1", "min_records=0", "api_port=9090", "home=../temp"};
		var configuration = new DigitalTwinConfiguration(args);
		var workingDir = new File(configuration.home(), "picota");
		DigitalTwinBox box = new DigitalTwinBox(configuration, workingDir);
		factory = new CommandFactory(box);
	}


	@Test
	@Ignore
	public void should_prepare_data() throws IOException {
		Command.Result<io.picota.digitaltwin.model.DigitalTwin> resultDt = factory.build(ReadModelCommand.class, URL).execute();
		io.picota.digitaltwin.model.DigitalTwin dt = resultDt.resource();
		DigitalTwin.DigitalSubject ds = dt.graph().digitalTwin().digitalSubject(0);
		TrainDataPreparer preparer = new TrainDataPreparer(dt, 1000);
		Files.list(SUBJECT_DATASETS.toPath())
				.filter(f -> f.getFileName().toString().endsWith(".tsv"))
				.forEach(subjectFile -> {
					try {
						preparer.prepareData(ds, ds.inferenceModel(0), subjectFile.toFile());
					} catch (IOException e) {
						Logger.error(e);
					}
				});

	}

}
