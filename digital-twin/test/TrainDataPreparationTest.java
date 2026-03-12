import io.intino.alexandria.Resource;
import io.picota.digitaltwin.DigitalTwinBox;
import io.picota.digitaltwin.DigitalTwinConfiguration;
import io.picota.digitaltwin.control.commands.*;
import io.picota.digitaltwin.control.commands.trainvariablescommand.TrainDataPreparer;
import io.quassar.monentia.picota.DigitalTwin;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class TrainDataPreparationTest {
	public static final String SOLAR_PLANT = "32a497be-7330-41ac-8541-5934b9fbda7a";
	public static final String ID_2 = "91eb4373-d8f8-438b-ad9d-d0fd980a09b1";
	public static final String BASE_PATH = "../runtime.test/data";
	public static final File SUBJECT_DATASET = new File("/Users/oroncal/workspace/projects/picota/temp/data_infecar/raw/infecar.csv");
	public static final File SUBJECT_2_DATASET = new File(BASE_PATH + "/house/house.csv");
	public static final File SUBJECT_SYNTHETIC_TRAIN_DATASET = new File(BASE_PATH + "/synthetic/audit_test_shift.tsv");
	public static final File SUBJECT_3_DATASETS = new File(BASE_PATH + "/spanish_homes");
	public static final String URL = "https://quassar.io/commits/" + SOLAR_PLANT;
	private DigitalTwinBox box;
	private CommandFactory factory;

	@Before
	public void setUp() {
		String[] args = {"python_venv=/Users/oroncal/workspace/projects/picota-smartbeach/.venv1", "min_records=0", "api_port=9090", "home=../temp"};
		var configuration = new DigitalTwinConfiguration(args);
		var workingDir = new File(configuration.home(), "picota");
		box = new DigitalTwinBox(configuration, workingDir);
		factory = new CommandFactory(box);
	}

	@Test
	public void should_train_model() {
		Command.Result<io.picota.digitaltwin.model.DigitalTwin> resultDt = factory.build(ReadModelCommand.class, URL).execute();
		if (!resultDt.success()) Assert.fail(resultDt.remarks());
		var result = factory.build(BuildModelCommand.class, SOLAR_PLANT, "or@monentia.es", new Resource(SUBJECT_DATASET)).execute();
		if (!result.success()) Assert.fail(result.remarks());
		result = factory.build(TrainSubjectsCommand.class, SOLAR_PLANT).execute();
		if (!result.success()) Assert.fail(result.remarks());
	}

	@Test
	@Ignore
	public void should_prepare_data() throws IOException {
		Command.Result<io.picota.digitaltwin.model.DigitalTwin> resultDt = factory.build(ReadModelCommand.class, URL).execute();
		io.picota.digitaltwin.model.DigitalTwin dt = resultDt.resource();
		DigitalTwin.DigitalSubject ds = dt.graph().digitalTwin().digitalSubject(0);
		File dir = new File("../temp/test");
		FileUtils.deleteDirectory(dir);
		TrainDataPreparer preparer = new TrainDataPreparer(dt, 1000);
		preparer.prepareData(ds, ds.inferenceModel(0), SUBJECT_DATASET);
	}

	@Test
	@Ignore
	public void should_prepare_spanish_homes_dataset() throws IOException {
		Command.Result<io.picota.digitaltwin.model.DigitalTwin> resultDt = factory.build(ReadModelCommand.class, URL).execute();
		io.picota.digitaltwin.model.DigitalTwin dt = resultDt.resource();
		DigitalTwin.DigitalSubject ds = dt.graph().digitalTwin().digitalSubject(0);
		File dir = new File("../temp/test");
		FileUtils.deleteDirectory(dir);
		TrainDataPreparer preparer = new TrainDataPreparer(dt, 1000);
		preparer.prepareData(ds, ds.inferenceModel(0), SUBJECT_2_DATASET);
	}


	@Test
	@Ignore
	public void should_prepare_synthetic_dataset() throws IOException {
		String URL = "https://quassar.io/commits/" + "effbd248-8c78-41cb-b900-19f1b8326c45";
		Command.Result<io.picota.digitaltwin.model.DigitalTwin> resultDt = factory.build(ReadModelCommand.class, URL).execute();
		io.picota.digitaltwin.model.DigitalTwin dt = resultDt.resource();
		DigitalTwin.DigitalSubject ds = dt.graph().digitalTwin().digitalSubject(0);
		File dir = new File("../temp/test");
		FileUtils.deleteDirectory(dir);
		TrainDataPreparer preparer = new TrainDataPreparer(dt, 1000);
		preparer.prepareData(ds, ds.inferenceModel(0), new File(BASE_PATH + "/synthetic/audit_train.tsv"));
		preparer.prepareData(ds, ds.inferenceModel(0), new File(BASE_PATH + "/synthetic/audit_test_shift.tsv"));
	}

	@Test
	@Ignore
	public void should_prepare_solar_plant_dataset() throws IOException {
		String URL = "https://quassar.io/commits/" + "effbd248-8c78-41cb-b900-19f1b8326c45";
		Command.Result<io.picota.digitaltwin.model.DigitalTwin> resultDt = factory.build(ReadModelCommand.class, URL).execute();
		io.picota.digitaltwin.model.DigitalTwin dt = resultDt.resource();
		DigitalTwin.DigitalSubject ds = dt.graph().digitalTwin().digitalSubject(0);
		File dir = new File("../temp/test");
		FileUtils.deleteDirectory(dir);
		TrainDataPreparer preparer = new TrainDataPreparer(dt, 1000);
		preparer.prepareData(ds, ds.inferenceModel(0), new File(BASE_PATH + "/synthetic/audit_train.tsv"));
		preparer.prepareData(ds, ds.inferenceModel(0), new File(BASE_PATH + "/synthetic/audit_test_shift.tsv"));
	}
}
