import io.intino.alexandria.Resource;
import io.picota.digitaltwin.builder.DigitalSubjectBuilder;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;

//file:///Users/oroncal/workspace/projects/picota/digital-twin/test-res/example/INFECAR-SOLAR-PLANT-1.0.0.zip
public class DTTest {
	public static final String DIGITAL_TWIN_NAME = "Infecar-Twin";

	@Before
	public void setUp() {
	}

	@Test
	public void migrate() throws IOException {
//		new InfecarDataToCSV(Main.class.getResourceAsStream("/infecar.jsonl"), new File("./test-res/infecar.csv")).run();
	}

	@Test
	public void should_send_data() throws InterruptedException {
		DigitalSubjectBuilder builder = new DigitalSubjectBuilder(new File("../temp/tests"), new HashMap<>(), new File("../runtime.evaluator/.venv"));
		builder.build("https://quassar.io/commits/5847cda2-27b0-4b82-9838-c1caa8dbb2ef", new Resource("Infecar-SolarPlant-DigitalTwin-lite.zip", new File("/Users/oroncal/workspace/projects/picota/digital-twin/test-res/example/Infecar-SolarPlant-DigitalTwin-lite.zip")), r -> {
			System.out.println(r.report());
		});
		CountDownLatch cdl = new CountDownLatch(1);
		cdl.await();

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