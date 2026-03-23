package io.picota.backend.control.ui.schemas;

public record PurchaseCreditsResponse(
		Integer balance,
		Integer creditsAdded,
		String transactionId
) {
}
