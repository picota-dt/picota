import io.picota.digitaltwin.control.commands.trainvariablescommand.TrainReportBuilder;
import io.picota.digitaltwin.control.commands.trainvariablescommand.TrainReportBuilder.Inference;
import io.picota.digitaltwin.control.commands.trainvariablescommand.TrainReportBuilder.TrainReport;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class ReportGenerationTest {
	@Test
	public void should_generate_simple_report() throws IOException {
		File dest = new File("../temp/example-report.pdf");
		dest.delete();
		TrainReport inferenceReport = new TrainReport("32a497be-7330-41ac-8541-5934b9fbda7a", "Inference Report", Instant.now(), "picota.io/commits/32a497be-7330-41ac-8541-5934b9fbda7a", trainings());
		new TrainReportBuilder().generate(inferenceReport, dest);
	}

	private List<TrainReportBuilder.TrainedSubject> trainings() {
		return List.of(new TrainReportBuilder.TrainedSubject("SolarPlant", inferences()));

	}

	private List<Inference> inferences() {
		return List.of(
				new Inference("generation", 0, "kWh", "0.32", Map.of("temperature", 32., "hour", 11., "day", 10.)),
				new Inference("generation3", 0, "kWh", "0.32", Map.of("temperature", 32., "hour", 11.)),
				new Inference("generation2", 0, "kWh", "0.32", Map.of("temperature", 32., "hour", 11.)),
				new Inference("generation2", 0, "kWh", "0.32", Map.of("temperature", 32., "hour", 11.)),
				new Inference("generation6", 0, "kWh", "0.32", Map.of("temperature", 32., "hour", 11.)),
				new Inference("generation7", 0, "kWh", "0.32", Map.of("temperature", 32., "hour", 11.)),
				new Inference("generation8", 0, "kWh", "0.32", Map.of("temperature", 32., "hour", 11.)),
				new Inference("generation8", 6, "kWh", "0.32", Map.of("temperature", 32., "hour", 11.))
		);
	}
}
