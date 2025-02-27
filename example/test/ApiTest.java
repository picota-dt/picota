import accessor.PicotaApiAccessor;
import io.intino.alexandria.exceptions.BadRequest;
import io.intino.alexandria.exceptions.InternalServerError;
import io.picota.example.picota.InfecarDataPreparer;
import io.picota.example.picota.Main;
import org.junit.Ignore;
import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;

import static accessor.PicotaApiAccessor.DataType.csv;

@Ignore
public class ApiTest {

	@Test
	public void should_sned_data_api() throws MalformedURLException, BadRequest, InternalServerError {
		PicotaApiAccessor accessor = new PicotaApiAccessor(new URL("http://localhost:9000"));
		accessor.postData(InfecarDataPreparer.SS, csv, () -> Main.class.getResourceAsStream("/infecar.csv"));
	}

	@Test
	public void should_start_training() throws InternalServerError, MalformedURLException {
		PicotaApiAccessor accessor = new PicotaApiAccessor(new URL("http://localhost:9000"));
		accessor.postState(InfecarDataPreparer.SS, PicotaApiAccessor.StateValue.Training);
	}
}
