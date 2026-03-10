package usecases;

import io.picota.digitaltwin.DigitalTwinBox;
import io.picota.digitaltwin.DigitalTwinConfiguration;
import io.picota.digitaltwin.control.commands.CommandFactory;
import io.picota.digitaltwin.control.commands.ReadModelCommand;
import io.picota.digitaltwin.control.commands.trainvariablescommand.TrainDataPreparer;
import io.quassar.monentia.picota.DigitalTwin;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class EuroplatanoTest {
	private static final String BASE_PATH = "../runtime.test/data/europlatano/datasets/";
	private static final String ID_NO_SPLIT = "c4b8ab46-7028-407f-8c5a-28261698eabd";
	private static final String ID_SPLIT = "f65755fc-5fd4-4608-b2df-5d521d4fcd67";
	private static final File CatSplittedByDay = new File(BASE_PATH + "day/split_category/produccion_agregada_cat.tsv");
	private static final File NoSplitByDay = new File(BASE_PATH + "day/no_split/produccion_agregada.tsv");
	private static final File CatSplittedByMonth = new File(BASE_PATH + "month/split_category/produccion_agregada_cat.tsv");
	private static final File NoSplitByMonth = new File(BASE_PATH + "month/no_split/produccion_agregada.tsv");
	private DigitalTwinBox box;
	private File workingDir;


	@Before
	public void setUp() {
		String[] args = {"python_venv=/Users/oroncal/workspace/projects/picota-smartbeach/.venv1", "min_records=0", "api_port=9090", "home=../temp"};
		var configuration = new DigitalTwinConfiguration(args);
		workingDir = new File(configuration.home(), "picota");
		box = new DigitalTwinBox(configuration, workingDir);
	}

	@Test
	@Ignore
	public void should_prepare_data_day_no_split_W20() throws IOException {
		if (workspace(ID_NO_SPLIT).exists()) FileUtils.deleteDirectory(workspace(ID_NO_SPLIT));
		var dt = new CommandFactory(box).build(ReadModelCommand.class, url(ID_NO_SPLIT)).execute().resource();
		DigitalTwin.DigitalSubject ds = dt.graph().digitalTwin().digitalSubject(0);
		DigitalTwin.DigitalSubject.InferenceModel.Lookback.Window window = ds.inferenceModel(0).lookback().asWindow();
		new TrainDataPreparer(dt, 1000).prepareData(ds, ds.inferenceModel(0), NoSplitByDay);
		Files.move(workspace(ID_NO_SPLIT).toPath(), new File(NoSplitByDay.getParentFile(), "W" + window.size()).toPath(), StandardCopyOption.REPLACE_EXISTING);
	}

	@Test
	@Ignore
	public void should_prepare_data_day_no_split_Window10() throws IOException {
		if (workspace(ID_NO_SPLIT).exists()) FileUtils.deleteDirectory(workspace(ID_NO_SPLIT));
		var dt = new CommandFactory(box).build(ReadModelCommand.class, url(ID_NO_SPLIT)).execute().resource();
		DigitalTwin.DigitalSubject ds = dt.graph().digitalTwin().digitalSubject(0);
		ds.inferenceModel(0).lookback().delete$();
		DigitalTwin.DigitalSubject.InferenceModel.Lookback.Window window = ds.inferenceModel(0)
				.create().lookback().asWindow(10);
		new TrainDataPreparer(dt, 1000).prepareData(ds, ds.inferenceModel(0), NoSplitByDay);
		Files.move(workspace(ID_NO_SPLIT).toPath(), new File(NoSplitByDay.getParentFile(), "W" + window.size()).toPath(), StandardCopyOption.REPLACE_EXISTING);
	}

	@Test
	@Ignore
	public void should_prepare_data_day_splitted_w10() throws IOException {
		if (workspace(ID_SPLIT).exists()) FileUtils.deleteDirectory(workspace(ID_SPLIT));
		var dt = new CommandFactory(box).build(ReadModelCommand.class, url(ID_SPLIT)).execute().resource();
		DigitalTwin.DigitalSubject ds = dt.graph().digitalTwin().digitalSubject(0);
		DigitalTwin.DigitalSubject.InferenceModel.Lookback.Window window = ds.inferenceModel(0).lookback().asWindow();
		window.size(10);
		new TrainDataPreparer(dt, 0).prepareData(ds, ds.inferenceModel(0), CatSplittedByDay);
		Files.move(workspace(ID_SPLIT).toPath(), new File(CatSplittedByDay.getParentFile(), "W" + window.size()).toPath(), StandardCopyOption.REPLACE_EXISTING);
	}

	@NotNull
	private File workspace(String id) {
		return new File(workingDir, "/workspace/" + id + "/data");
	}

	@Test
	@Ignore
	public void should_prepare_data_month_no_split() throws IOException {
		var dt = new CommandFactory(box).build(ReadModelCommand.class, url(ID_NO_SPLIT)).execute().resource();
		DigitalTwin.DigitalSubject ds = dt.graph().digitalTwin().digitalSubject(0);
		ds.resolution().scale(DigitalTwin.DigitalSubject.Resolution.Scale.Months);
		new TrainDataPreparer(dt, 100).prepareData(ds, ds.inferenceModel(0), NoSplitByMonth);
	}

	@Test
	@Ignore
	public void should_prepare_data_month_splitted() throws IOException {
		var dt = new CommandFactory(box).build(ReadModelCommand.class, url(ID_SPLIT)).execute().resource();
		DigitalTwin.DigitalSubject ds = dt.graph().digitalTwin().digitalSubject(0);
		new TrainDataPreparer(dt, 100).prepareData(ds, ds.inferenceModel(0), CatSplittedByMonth);
	}

	@NotNull
	private static String url(String id) {
		return "https://quassar.io/commits/" + id;
	}
}
