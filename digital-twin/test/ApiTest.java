import com.google.gson.GsonBuilder;
import io.intino.alexandria.exceptions.AlexandriaException;
import io.intino.alexandria.exceptions.BadRequest;
import io.intino.alexandria.exceptions.InternalServerError;
import io.picota.digitaltwin.PicotaDigitaltwinAccessor;
import io.picota.digitaltwin.schemas.Inference;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;


@Ignore
public class ApiTest {

	private String token;

	@Before
	public void setUp() {
		try {
			token = new String(this.getClass().getResourceAsStream("/API_TOKEN.txt").readAllBytes());
		} catch (IOException e) {
			token = "";
		}
	}

	@Test
	public void should_send_data_api() throws MalformedURLException, BadRequest, InternalServerError {
//		PicotaDigitaltwinAccessor accessor = new PicotaDigitaltwinAccessor(new URL("http://localhost:9090"));
//		accessor.postData(DTTest.DIGITAL_TWIN_NAME, csv, () -> Main.class.getResourceAsStream("/infecar.csv"));
	}

	@Test
	public void should_infer() throws AlexandriaException, MalformedURLException {
		PicotaDigitaltwinAccessor accessor = new PicotaDigitaltwinAccessor(new URL("http://localhost:9090"), "");
		List<Inference> result = accessor.postInference(DTTest.DIGITAL_TWIN_NAME, Map.of(
				"instant", "2024-04-18T16:11:00Z",
				"operational_cellTemperature", 25.40,
				"weather_temperature", 24.00,
				"weather_radiation", 136.80,
				"generatedReactivePower", 6.32,
				"generatedActivePower", 6.32,
				"consumedReactivePower", 137.25,
				"consumedActivePower", 137.25
		));
		System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(result));
	}

}
