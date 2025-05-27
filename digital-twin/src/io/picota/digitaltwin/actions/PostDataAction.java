package io.picota.digitaltwin.actions;

import io.intino.alexandria.Context;
import io.intino.alexandria.exceptions.AlexandriaException;
import io.intino.alexandria.exceptions.BadRequest;
import io.picota.digitaltwin.DigitalTwinBox;

public class PostDataAction implements io.intino.alexandria.rest.RequestErrorHandler {
	public DigitalTwinBox box;
//	public PostDataResource.Type type;
	public String digitalTwin;
	public Context context;
	public String subject;
	public io.intino.alexandria.Resource data;

	public void execute() throws BadRequest {
//		try {
//			new DataFeeder(box).feed(subject, type.name(), data.stream());
//		} catch (IOException e) {
//			Logger.error(e);
//		} catch (Exception e) {
//			Logger.error(e);
//			throw new BadRequest(e.getMessage());
//		}
	}


	public void onMalformedRequest(Throwable e) throws AlexandriaException {
		throw new BadRequest("Malformed request");
	}
}