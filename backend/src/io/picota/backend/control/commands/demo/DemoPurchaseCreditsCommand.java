package io.picota.backend.control.commands.demo;

import io.picota.backend.control.commands.PurchaseCreditsCommand;
import io.picota.backend.control.ui.schemas.PurchaseCreditsResponse;
import io.picota.backend.control.ui.schemas.requests.PurchaseCreditsRequest;

public final class DemoPurchaseCreditsCommand implements PurchaseCreditsCommand {
	private final DemoCommandState state;

	public DemoPurchaseCreditsCommand(DemoCommandState state) {
		this.state = state;
	}

	@Override
	public PurchaseCreditsResponse purchaseCredits(String authToken, PurchaseCreditsRequest request) {
		return state.purchaseCredits(authToken, request);
	}
}
