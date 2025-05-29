import io.intino.alexandria.Resource;
import io.picota.digitaltwin.builder.DigitalSubjectBuilder;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;

//file:///Users/oroncal/workspace/projects/picota/digital-twin/test-res/example/INFECAR-SOLAR-PLANT-1.0.0.zip
@Ignore
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
	public void should_send_data() throws InterruptedException, IOException {
		DigitalSubjectBuilder builder = new DigitalSubjectBuilder(new File("../temp/tests"), new HashMap<>(), new File("../.venv").getCanonicalFile());
		builder.build("https://quassar.io/commits/d36f0eec-2197-4235-bf46-236ece739fe7", new Resource("Camera_acc_alo.tsv.zip", new File("/Users/oroncal/workspace/projects/picota/digital-twin/test-res/Camera_acc_alo.tsv.zip")), r -> {
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