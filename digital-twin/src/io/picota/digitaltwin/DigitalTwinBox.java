package io.picota.digitaltwin;

import io.intino.alexandria.http.AlexandriaHttpServerBuilder;
import io.picota.digitaltwin.control.DigitalTwinsStore;
import io.picota.digitaltwin.control.commands.CommandFactory;
import io.picota.digitaltwin.ui.UiService;

import java.io.File;

public class DigitalTwinBox extends AbstractBox {
	private final File workspaceDir;
	private DigitalTwinsStore store;

	public DigitalTwinBox(DigitalTwinConfiguration conf, File workingDir) {
		super(conf);
		workspaceDir = new File(workingDir, "workspace");
		store = new DigitalTwinsStore(new File(workingDir, "store"));
	}

	@Override
	public io.intino.alexandria.core.Box put(Object o) {
		super.put(o);
		return this;
	}

	public File workspaceDir() {
		return workspaceDir;
	}

	public DigitalTwinsStore store() {
		return store;
	}

	public void beforeStart() {
		store.load(this);
		AlexandriaHttpServerBuilder.setup(Integer.parseInt(configuration.apiPort()), "www/");
		AlexandriaHttpServerBuilder.setUI(true);
		new UiService(store, new CommandFactory(this)).start();
	}

	public void afterStart() {
	}

	public void beforeStop() {
	}

	public void afterStop() {
	}
}