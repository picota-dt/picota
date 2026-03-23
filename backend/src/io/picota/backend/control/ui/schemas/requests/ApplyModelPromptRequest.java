package io.picota.backend.control.ui.schemas.requests;

public record ApplyModelPromptRequest(
		String prompt,
		String currentContent
) {
}
