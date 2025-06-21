package io.picota.digitaltwin.actions;

import io.picota.digitaltwin.DigitalTwinBox;
import io.picota.digitaltwin.model.DigitalTwin;


public class QuotaResetAction {
	public DigitalTwinBox box;

	public void execute() {
		box.store().all().forEach(DigitalTwin::resetQuota);
	}
}