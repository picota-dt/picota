package io.picota.backend.control.ui.lsp;

import io.intino.ls.document.DocumentManager;
import io.picota.backend.control.commands.UiCommandSet;
import io.picota.backend.control.ui.schemas.DigitalTwin;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TwinModelDocumentManager implements DocumentManager {
	private static final URI ROOT = URI.create("inmemory://picota");
	private static final URI TWINS_FOLDER = URI.create("inmemory://picota/twins");
	private static final Pattern TWIN_URI_PATTERN = Pattern.compile(".*/twins/([^/]+?)(?:\\.[^./]+)?$");
	private static final String DEFAULT_LANGUAGE = "tara";

	private final UiCommandSet commands;
	private final String authToken;
	private final ConcurrentMap<URI, DocumentEntry> documents = new ConcurrentHashMap<>();
	private final List<Consumer<FileEvent>> listeners = new CopyOnWriteArrayList<>();

	public TwinModelDocumentManager(UiCommandSet commands, String authToken) {
		this.commands = Objects.requireNonNull(commands, "commands");
		this.authToken = authToken == null ? "" : authToken.trim();
		loadUserDocuments();
	}

	@Override
	public URI root() {
		return ROOT;
	}

	@Override
	public List<URI> all() {
		return new ArrayList<>(documents.keySet());
	}

	@Override
	public List<URI> folders() {
		return List.of(TWINS_FOLDER);
	}

	@Override
	public void onChange(Consumer<FileEvent> consumer) {
		if (consumer != null) listeners.add(consumer);
	}

	@Override
	public void upsertDocument(URI uri, String language, String text) {
		URI normalized = normalizeUri(uri);
		DocumentEntry previous = documents.get(normalized);
		String twinId = previous != null ? previous.twinId() : twinIdOf(normalized);
		int version = previous == null ? 1 : previous.version() + 1;
		DocumentEntry next = new DocumentEntry(
				language == null || language.isBlank() ? DEFAULT_LANGUAGE : language,
				text == null ? "" : text,
				version,
				twinId
		);
		documents.put(normalized, next);
		if (previous == null) notifyChange(new FileEvent(normalized.toString(), FileChangeType.Created));
		else notifyChange(new FileEvent(normalized.toString(), FileChangeType.Changed));
	}

	@Override
	public InputStream getDocumentText(URI uri) {
		URI normalized = normalizeUri(uri);
		DocumentEntry existing = documents.get(normalized);
		if (existing != null) {
			return new ByteArrayInputStream(existing.text().getBytes(StandardCharsets.UTF_8));
		}
		DocumentEntry loaded = fetchTwinDocument(normalized);
		if (loaded == null) return null;
		documents.put(normalized, loaded);
		return new ByteArrayInputStream(loaded.text().getBytes(StandardCharsets.UTF_8));
	}

	@Override
	public void move(URI from, URI to) {
		URI normalizedFrom = normalizeUri(from);
		URI normalizedTo = normalizeUri(to);
		DocumentEntry current = documents.remove(normalizedFrom);
		if (current == null) return;
		documents.put(normalizedTo, current.withTwinId(twinIdOf(normalizedTo)));
		notifyChange(new FileEvent(normalizedFrom.toString(), FileChangeType.Deleted));
		notifyChange(new FileEvent(normalizedTo.toString(), FileChangeType.Created));
	}

	@Override
	public void remove(URI uri) {
		URI normalized = normalizeUri(uri);
		if (documents.remove(normalized) != null) {
			notifyChange(new FileEvent(normalized.toString(), FileChangeType.Deleted));
		}
	}

	private void loadUserDocuments() {
		if (authToken.isBlank()) return;
		try {
			List<DigitalTwin> twins = commands.listTwins(authToken, null, null, null, null, null);
			for (DigitalTwin twin : twins) {
				if (twin == null || twin.id() == null || twin.id().isBlank()) continue;
				URI uri = uriOfTwin(twin.id());
				String modelText = twin.model() == null ? "" : twin.model();
				documents.put(uri, new DocumentEntry(DEFAULT_LANGUAGE, modelText, 1, twin.id()));
			}
		} catch (RuntimeException ignored) {
		}
	}

	private DocumentEntry fetchTwinDocument(URI uri) {
		if (authToken.isBlank()) return null;
		String twinId = twinIdOf(uri);
		if (twinId.isBlank()) return null;
		try {
			String content = commands.getModel(authToken, twinId).content();
			return new DocumentEntry(DEFAULT_LANGUAGE, content == null ? "" : content, 1, twinId);
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private static URI uriOfTwin(String twinId) {
		String safeTwinId = twinId.trim();
		return URI.create("inmemory://picota/twins/" + safeTwinId + ".tara");
	}

	private static URI normalizeUri(URI uri) {
		if (uri == null) return URI.create("inmemory://picota/twins/unknown.tara");
		return URI.create(uri.toString());
	}

	private static String twinIdOf(URI uri) {
		String path = uri == null || uri.getPath() == null ? "" : uri.getPath();
		Matcher matcher = TWIN_URI_PATTERN.matcher(path);
		if (!matcher.matches()) return "";
		return matcher.group(1);
	}

	private void notifyChange(FileEvent event) {
		for (Consumer<FileEvent> listener : listeners) {
			try {
				listener.accept(event);
			} catch (RuntimeException ignored) {
			}
		}
	}

	private record DocumentEntry(String language, String text, int version, String twinId) {
		private DocumentEntry withTwinId(String nextTwinId) {
			return new DocumentEntry(language, text, version, nextTwinId);
		}
	}
}
