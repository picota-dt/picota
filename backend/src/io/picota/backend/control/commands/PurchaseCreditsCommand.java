package io.picota.backend.control.commands;

import io.picota.backend.control.ui.schemas.PurchaseCreditsResponse;
import io.picota.backend.control.ui.schemas.requests.PurchaseCreditsRequest;

@FunctionalInterface
public interface PurchaseCreditsCommand {
	PurchaseCreditsResponse purchaseCredits(String authToken, PurchaseCreditsRequest request);
}
