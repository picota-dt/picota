import io.intino.alexandria.Main;
import io.intino.alexandria.exceptions.AlexandriaException;
import io.intino.alexandria.exceptions.BadRequest;
import io.intino.alexandria.exceptions.InternalServerError;
import io.picota.digitalmodel.PicotaApiAccessor;
import org.junit.Ignore;
import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;


@Ignore
public class ApiTest {

	@Test
	public void should_send_data_api() throws MalformedURLException, BadRequest, InternalServerError {
//		PicotaApiAccessor accessor = new PicotaApiAccessor(new URL("http://localhost:9090"));
//		accessor.postData(DTTest.DIGITAL_TWIN_NAME, csv, () -> Main.class.getResourceAsStream("/infecar.csv"));
	}

	@Test
	public void should_start_training() throws AlexandriaException, MalformedURLException {
		PicotaApiAccessor accessor = new PicotaApiAccessor(new URL("http://localhost:9090"));
		accessor.postState(DTTest.DIGITAL_TWIN_NAME, PicotaApiAccessor.StateValue.Training);
	}

	@Test
	public void should_start_operating() throws AlexandriaException, MalformedURLException {
		PicotaApiAccessor accessor = new PicotaApiAccessor(new URL("http://localhost:9090"));
		accessor.postState(DTTest.DIGITAL_TWIN_NAME, PicotaApiAccessor.StateValue.Operating);
	}
}
