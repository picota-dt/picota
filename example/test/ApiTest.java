import accessor.PicotaApiAccessor;
import io.intino.alexandria.exceptions.BadRequest;
import io.intino.alexandria.exceptions.InternalServerError;
import io.picota.example.picota.InfecarDataPreparer;
import io.picota.example.picota.Main;
import org.junit.Ignore;
import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;

@Ignore
public class ApiTest {

	@Test
	public void should_sned_data_api() throws MalformedURLException, BadRequest, InternalServerError {
		PicotaApiAccessor accessor = new PicotaApiAccessor(new URL("http://localhost:9000"));
		accessor.postData(InfecarDataPreparer.SS, () -> Main.class.getResourceAsStream("/infecar.csv"));
	}
}
