import io.intino.alexandria.Resource;
import io.picota.digitaltwin.DigitalTwinBox;
import io.picota.digitaltwin.DigitalTwinConfiguration;
import io.picota.digitaltwin.control.commands.*;
import io.picota.digitaltwin.control.commands.trainvariablescommand.TrainDataPreparer;
import io.quassar.monentia.picota.DigitalTwin;
import io.quassar.monentia.picota.PicotaGraph;
import io.quassar.monentia.picota.PicotaModel;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class TrainDataPreparationTest {


	public static final String ID = "32a497be-7330-41ac-8541-5934b9fbda7a";
	public static final File SUBJECT_DATASET = new File("/Users/oroncal/workspace/projects/picota/digital-twin/test-res/example/lite/SolarPlant.csv");
	public static final String URL = "https://quassar.io/commits/32a497be-7330-41ac-8541-5934b9fbda7a";
	private DigitalTwinBox box;
	private CommandFactory factory;

	@Before
	public void setUp() {
		String[] args = {"python_venv=/Users/oroncal/workspace/projects/picota-smartbeach/.venv1", "min_records=0", "api_port=9090", "home=../temp"};
		var configuration = new DigitalTwinConfiguration(args);
		var workingDir = new File(configuration.home(), "picota");
		if (args.length > 4 && args[3].equals("--model")) configuration.args().put("model", args[4]);
		box = new DigitalTwinBox(configuration, workingDir);
		factory = new CommandFactory(box);
	}

	@Test
	public void should_train_model() {
		Command.Result<io.picota.digitaltwin.model.DigitalTwin> resultDt = factory.build(ReadModelCommand.class, URL).execute();
		if (!resultDt.success()) Assert.fail(resultDt.remarks());
		var result = factory.build(BuildModelCommand.class, ID, "or@monentia.es", new Resource(SUBJECT_DATASET)).execute();
		if (!result.success()) Assert.fail(result.remarks());
		result = factory.build(TrainSubjectsCommand.class, ID).execute();
		if (!result.success()) Assert.fail(result.remarks());
	}

	@Test
	@Ignore
	public void name() throws IOException {
		PicotaGraph graph = PicotaModel.parse(URL);
		File dir = new File("../temp/test");
		FileUtils.deleteDirectory(dir);
		TrainDataPreparer preparer = new TrainDataPreparer(null, 1000);
		DigitalTwin.DigitalSubject ds = graph.digitalTwin().digitalSubject(0);
		File subjectDataset = SUBJECT_DATASET;
		preparer.prepareData(ds, ds.inferenceModel(0), subjectDataset);
	}
}
