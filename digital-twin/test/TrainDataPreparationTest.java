import io.picota.digitaltwin.control.commands.trainvariablescommand.TrainDataPreparer;
import io.picota.digitaltwin.model.Archetype;
import io.quassar.monentia.picota.DigitalTwin;
import io.quassar.monentia.picota.ModelParser;
import io.quassar.monentia.picota.PicotaGraph;
import org.apache.commons.io.FileUtils;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class TrainDataPreparationTest {

	@Test
	@Ignore
	public void name() throws URISyntaxException, IOException {
		ModelParser.Model model = ModelParser.parse(new URI("https://quassar.io/commits/32a497be-7330-41ac-8541-5934b9fbda7a").toURL());
		PicotaGraph graph = model.graph();
		File dir = new File("../temp/test");
		FileUtils.deleteDirectory(dir);
		TrainDataPreparer preparer = new TrainDataPreparer(new Archetype(dir), null);
		DigitalTwin.DigitalSubject ds = graph.digitalTwin().digitalSubject(0);
		File subjectDataset = new File("/Users/oroncal/workspace/projects/picota/digital-twin/test-res/example/lite/SolarPlant.csv");
		preparer.prepareData(ds, ds.inferenceModel(0), subjectDataset);

	}
}
