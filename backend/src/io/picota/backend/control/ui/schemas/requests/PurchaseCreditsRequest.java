package io.picota.backend.control.ui.schemas.requests;

import io.picota.backend.control.ui.schemas.CreditPack;

public record PurchaseCreditsRequest(
		CreditPack pack,
		String paymentMethodId
) {
}
