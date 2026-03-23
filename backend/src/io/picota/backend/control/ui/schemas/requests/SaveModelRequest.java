package io.picota.backend.control.ui.schemas.requests;

import io.picota.backend.control.ui.schemas.VersionBump;

public record SaveModelRequest(
		String content,
		VersionBump versionBump,
		String commitMessage
) {
}
