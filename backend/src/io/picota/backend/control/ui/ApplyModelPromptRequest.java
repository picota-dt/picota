package io.picota.backend.control.ui;

public record ApplyModelPromptRequest(
		String prompt,
		String currentContent
) {
}
