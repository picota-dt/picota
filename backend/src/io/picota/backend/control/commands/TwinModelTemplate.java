package io.picota.backend.control.commands;

public final class TwinModelTemplate {
	private static final String DEFAULT_TEMPLATE =
			"# ${twinName} — Digital Twin Model\nsubjects: []\nconstraints: []\n";

	private final String template;

	private TwinModelTemplate(String template) {
		this.template = ensureTrailingNewline(template);
	}

	public static TwinModelTemplate defaultTemplate() {
		return new TwinModelTemplate(DEFAULT_TEMPLATE);
	}

	public static TwinModelTemplate fromRaw(String rawTemplate) {
		if (rawTemplate == null || rawTemplate.isBlank()) return defaultTemplate();
		return new TwinModelTemplate(rawTemplate);
	}

	public String render(String twinName) {
		String safeTwinName = (twinName == null || twinName.isBlank()) ? "Digital Twin" : twinName.trim();
		String rendered = template
				.replace("${twinName}", safeTwinName)
				.replace("{{twinName}}", safeTwinName)
				.replace("__TWIN_NAME__", safeTwinName);
		return ensureTrailingNewline(rendered);
	}

	public boolean matchesRendered(String candidate, String twinName) {
		if (candidate == null) return false;
		return normalize(candidate).equals(normalize(render(twinName)));
	}

	private static String normalize(String value) {
		if (value == null) return "";
		return value.replace("\r\n", "\n").trim();
	}

	private static String ensureTrailingNewline(String value) {
		if (value == null || value.isBlank()) return "\n";
		return value.endsWith("\n") ? value : value + "\n";
	}
}
