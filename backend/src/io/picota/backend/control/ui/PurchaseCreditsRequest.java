package io.picota.backend.control.ui;

public record PurchaseCreditsRequest(
		CreditPack pack,
		String paymentMethodId
) {
}
