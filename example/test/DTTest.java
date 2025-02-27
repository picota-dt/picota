import accessor.PicotaApiAccessor;
import io.intino.alexandria.Resource;
import io.intino.alexandria.exceptions.BadRequest;
import io.intino.alexandria.exceptions.InternalServerError;
import io.picota.example.picota.GraphLoader;
import io.picota.example.picota.InfecarDataPreparer;
import io.picota.example.picota.InfecarDataToCSV;
import io.picota.example.picota.Main;
import io.picota.runtime.PicotaStarter;
import io.picota.runtime.RuntimeBox;
import io.picota.runtime.actions.GetStateAction;
import io.picota.runtime.actions.PostDataAction;
import io.picota.runtime.actions.PostStateAction;
import io.picota.runtime.rest.resources.PostStateResource;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.ExecutionException;

public class DTTest {

	private RuntimeBox box;

	@Before
	public void setUp() {
		String[] args = args();
		box = PicotaStarter.start(args, GraphLoader.load(args));
		Runtime.getRuntime().addShutdownHook(new Thread(box::stop));
	}

	@Test
	public void migrate() throws IOException {
		new InfecarDataToCSV(Main.class.getResourceAsStream("/infecar.jsonl"), new File("./test-res/infecar.csv")).run();
	}

	@Test
	public void should_send_data() throws BadRequest, InterruptedException {
		PostDataAction action = new PostDataAction();
		action.entity = InfecarDataPreparer.SS;
		action.box = box;
		action.data = new Resource(InfecarDataPreparer.SS, "text/csv", Main.class.getResourceAsStream("/infecar.csv"));
		action.execute();
		Thread.sleep(10000);
	}

	@Test
	public void should_prepare_models() throws BadRequest, InterruptedException, ExecutionException {
		GetStateAction action = new GetStateAction();
		action.box = box;
		action.entity = InfecarDataPreparer.SS;
		System.out.println(action.execute());
		PostStateAction changeStateAction = new PostStateAction();
		changeStateAction.box = box;
		changeStateAction.entity = InfecarDataPreparer.SS;
		changeStateAction.value = PostStateResource.Value.Training;
		changeStateAction.execute();
		System.out.println(action.execute());
		synchronized (changeStateAction.training()) {
			changeStateAction.training().get();
		}
	}


	private static String[] args() {
		return new String[]{"broker_port=63000",
				"broker_secondary_port=1884",
				"home=./temp",
				"venv=/Users/oroncal/workspace/picota/example/venv",
				"backup_directory=./temp/backup",
				"datalake_directory=./temp/datalake",
				"api_port=9000"};
	}
}
