package io.picota.backend.control.ui;

public record SaveModelRequest(
		String content,
		VersionBump versionBump,
		String commitMessage
) {
}
