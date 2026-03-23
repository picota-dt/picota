package io.picota.backend.control.ui;

public record PurchaseCreditsResponse(
		Integer balance,
		Integer creditsAdded,
		String transactionId
) {
}
