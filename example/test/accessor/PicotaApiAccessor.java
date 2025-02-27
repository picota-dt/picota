package accessor;

import java.util.Map;
import java.util.HashMap;
import java.net.URL;
import java.lang.reflect.Type;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonDeserializer;
import io.intino.alexandria.exceptions.*;
import io.intino.alexandria.restaccessor.RequestBuilder;
import io.intino.alexandria.restaccessor.core.RestAccessorNotifier;


public class PicotaApiAccessor {
	private final URL url;
	private final RestAccessorNotifier notifier = new RestAccessorNotifier();
	private int timeoutMillis = 120 * 1_000;
	private io.intino.alexandria.restaccessor.OutBox outBox = null;
	private Map<String, String> additionalHeaders = new HashMap<>();

	public enum StateValue {
		Waiting, Training, Operating
	}

	public enum StatusMoment {
		Current, Future
	}

	public PicotaApiAccessor(URL url) {
		this.url = url;

	}

	public PicotaApiAccessor(URL url, int timeoutMillis) {
		this.url = url;
		this.timeoutMillis = timeoutMillis;

	}

	public PicotaApiAccessor(URL url, int timeoutMillis, java.io.File outBoxDirectory, int intervalRetrySeconds) {
		this.url = url;
		this.timeoutMillis = timeoutMillis;
		this.outBox = new io.intino.alexandria.restaccessor.OutBox(outBoxDirectory, intervalRetrySeconds);

	}

	public void addCommonHeader(String name, String value) {
		additionalHeaders.put(name, value);
	}

	public void addRequestSerializer(Type type, JsonSerializer<?> adapter) {
		io.intino.alexandria.restaccessor.adapters.RequestAdapter.addCustomAdapter(type, adapter);
	}

	public void addResponseDeserializer(Type type, JsonDeserializer<?> adapter) {
		io.intino.alexandria.restaccessor.adapters.ResponseAdapter.addCustomAdapter(type, adapter);
	}

	public Enum getState(String entity) throws InternalServerError {
		RequestBuilder builder = new RequestBuilder(this.url).timeOut(this.timeoutMillis);
		additionalHeaders.forEach((k,v) -> builder.headerParameter(k,v));
		RequestBuilder.Request request = builder
			.queryParameter("entity", entity)
			.build(RequestBuilder.Method.GET, "/api" + "/state");
		try {
			io.intino.alexandria.restaccessor.Response response = request.execute();
			return io.intino.alexandria.restaccessor.adapters.ResponseAdapter.adapt(response.content(), Enum.class);
		} catch (AlexandriaException e) {

			if (outBox != null) outBox.push(request);
			throw new InternalServerError(e.message());
		}
	}

	public void postState(String entity, StateValue value) throws InternalServerError {
		RequestBuilder builder = new RequestBuilder(this.url).timeOut(this.timeoutMillis);
		additionalHeaders.forEach((k,v) -> builder.headerParameter(k,v));
		RequestBuilder.Request request = builder
			.entityPart("entity", entity)
			.entityPart("value", value)
			.build(RequestBuilder.Method.POST, "/api" + "/state");
		try {
			io.intino.alexandria.restaccessor.Response response = request.execute();
		} catch (AlexandriaException e) {

			if (outBox != null) outBox.push(request);
			throw new InternalServerError(e.message());
		}
	}

	public void postData(String entity, io.intino.alexandria.Resource.InputStreamProvider data) throws BadRequest, InternalServerError {
		RequestBuilder builder = new RequestBuilder(this.url).timeOut(this.timeoutMillis);
		additionalHeaders.forEach((k,v) -> builder.headerParameter(k,v));
		RequestBuilder.Request request = builder
			.entityPart("entity", entity)
			.entityPart(new io.intino.alexandria.Resource("data", data))
			.build(RequestBuilder.Method.POST, "/api" + "/data");
		try {
			io.intino.alexandria.restaccessor.Response response = request.execute();
		} catch (AlexandriaException e) {
			if (e instanceof BadRequest) throw ((BadRequest) e);
			if (outBox != null) outBox.push(request);
			throw new InternalServerError(e.message());
		}
	}

	public String getStatus(StatusMoment moment, String entity) throws InternalServerError {
		RequestBuilder builder = new RequestBuilder(this.url).timeOut(this.timeoutMillis);
		additionalHeaders.forEach((k,v) -> builder.headerParameter(k,v));
		RequestBuilder.Request request = builder
			.queryParameter("moment", moment.name())
			.queryParameter("entity", entity)
			.build(RequestBuilder.Method.GET, "/api" + "/status");
		try {
			io.intino.alexandria.restaccessor.Response response = request.execute();
			return io.intino.alexandria.restaccessor.adapters.ResponseAdapter.adapt(response.content(), String.class);
		} catch (AlexandriaException e) {

			if (outBox != null) outBox.push(request);
			throw new InternalServerError(e.message());
		}
	}


}