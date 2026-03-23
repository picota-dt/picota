package io.picota.backend.control.commands.real;

import io.picota.backend.control.commands.PurchaseCreditsCommand;
import io.picota.backend.control.ui.schemas.PurchaseCreditsResponse;
import io.picota.backend.control.ui.schemas.requests.PurchaseCreditsRequest;

public final class RealPurchaseCreditsCommand implements PurchaseCreditsCommand {
	private final RealCommandState state;

	public RealPurchaseCreditsCommand(RealCommandState state) {
		this.state = state;
	}

	@Override
	public PurchaseCreditsResponse purchaseCredits(String authToken, PurchaseCreditsRequest request) {
		return state.purchaseCredits(authToken, request);
	}
}
