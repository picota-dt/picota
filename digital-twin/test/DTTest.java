import io.intino.alexandria.Resource;
import io.intino.alexandria.exceptions.BadRequest;
import io.picota.digitaltwin.DigitalTwinBox;
import io.picota.digitaltwin.Main;
import io.picota.digitaltwin.actions.GetStateAction;
import io.picota.digitaltwin.actions.PostDataAction;
import io.picota.digitaltwin.actions.PostStateAction;
import io.picota.digitaltwin.rest.resources.PostStateResource;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
//file:///Users/oroncal/workspace/projects/picota/digital-twin/test-res/example/INFECAR-SOLAR-PLANT-1.0.0.zip
public class DTTest {
	public static final String DIGITAL_TWIN_NAME = "Infecar-Twin";
	private DigitalTwinBox box;

	@Before
	public void setUp() {
		String[] args = args();
		box = Main.run(args);
	}

	@Test
	public void migrate() throws IOException {
//		new InfecarDataToCSV(Main.class.getResourceAsStream("/infecar.jsonl"), new File("./test-res/infecar.csv")).run();
	}

	@Test
	public void should_send_data() throws BadRequest, InterruptedException {
		PostDataAction action = new PostDataAction();
		action.digitalTwin = DIGITAL_TWIN_NAME;
		action.box = box;
		action.data = new Resource(DIGITAL_TWIN_NAME, "text/csv", Main.class.getResourceAsStream("/infecar.csv"));
		action.execute();
		Thread.sleep(10000);
	}

	@Test
	public void should_prepare_models() throws BadRequest {
		GetStateAction action = new GetStateAction();
		action.box = box;
		action.digitalTwin = DIGITAL_TWIN_NAME;
		System.out.println(action.execute());
		PostStateAction changeStateAction = new PostStateAction();
		changeStateAction.box = box;
		changeStateAction.digitalSubject = DIGITAL_TWIN_NAME;
		changeStateAction.value = PostStateResource.Value.Training;
		changeStateAction.execute();
		System.out.println(action.execute());
	}

	private static String[] args() {
		return new String[]{"broker_port=63000",
				"home=./temp",
				"venv=/Users/oroncal/workspace/picota/example/venv",
				"backup_directory=./temp/backup",
				"datalake_directory=./temp/datalake",
				"api_port=9000"};
	}
}