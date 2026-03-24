package io.picota.backend.control.ui.lsp;

import io.intino.ls.IntinoLanguageServer;
import io.javalin.config.RoutesConfig;
import io.javalin.websocket.*;
import io.picota.backend.control.commands.UiCommandSet;
import io.quassar.monentia.Picota;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ModelLspWebSocketEndpoint {
	private static final byte[] HEADER_SEPARATOR = "\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
	private final UiCommandSet commands;
	private final ConcurrentMap<String, SessionBridge> sessions = new ConcurrentHashMap<>();

	public ModelLspWebSocketEndpoint(UiCommandSet commands) {
		this.commands = commands;
	}

	public void register(RoutesConfig routes, String path) {
		routes.ws(path, this::configureHandlers);
	}

	public void closeAll() {
		sessions.values().forEach(SessionBridge::close);
		sessions.clear();
	}

	private void configureHandlers(WsConfig ws) {
		ws.onConnect(this::onConnect);
		ws.onMessage(this::onMessage);
		ws.onClose(this::onClose);
		ws.onError(this::onError);
	}

	private void onConnect(WsConnectContext ctx) {
		SessionBridge bridge = new SessionBridge(ctx, commands, authToken(ctx));
		SessionBridge previous = sessions.put(ctx.sessionId(), bridge);
		if (previous != null) previous.close();
	}

	private void onMessage(WsMessageContext ctx) {
		SessionBridge bridge = sessions.get(ctx.sessionId());
		if (bridge != null) bridge.consume(ctx.message());
	}

	private void onClose(WsCloseContext ctx) {
		SessionBridge bridge = sessions.remove(ctx.sessionId());
		if (bridge != null) bridge.close();
	}

	private void onError(WsErrorContext ctx) {
		SessionBridge bridge = sessions.remove(ctx.sessionId());
		if (bridge != null) bridge.close();
	}

	private static String authToken(WsContext ctx) {
		String tokenParam = ctx.queryParam("token");
		if (tokenParam != null && !tokenParam.isBlank()) return tokenParam.trim();
		String authorization = ctx.header("Authorization");
		if (authorization != null && authorization.startsWith("Bearer ")) {
			String token = authorization.substring("Bearer ".length()).trim();
			if (!token.isBlank()) return token;
		}
		return "";
	}

	private static final class SessionBridge {
		private final WsContext wsContext;
		private final java.io.PipedInputStream serverInput;
		private final java.io.PipedOutputStream clientOutput;
		private final JsonRpcFrameOutputStream serverOutput;
		private final IntinoLanguageServer languageServer;

		private SessionBridge(WsContext wsContext, UiCommandSet commands, String authToken) {
			this.wsContext = wsContext;
			try {
				this.serverInput = new java.io.PipedInputStream(64 * 1024);
				this.clientOutput = new java.io.PipedOutputStream(serverInput);
			} catch (IOException e) {
				throw new IllegalStateException("Unable to initialize LSP IO bridge", e);
			}
			this.serverOutput = new JsonRpcFrameOutputStream(this::sendMessage);
			this.languageServer = new IntinoLanguageServer(new Picota(), new TwinModelDocumentManager(commands, authToken));
			var launcher = LSPLauncher.createServerLauncher(languageServer, serverInput, serverOutput);
			LanguageClient remoteClient = launcher.getRemoteProxy();
			languageServer.connect(remoteClient);
			launcher.startListening();
		}

		private void consume(String payload) {
			if (payload == null || payload.isBlank()) return;
			byte[] body = payload.getBytes(StandardCharsets.UTF_8);
			byte[] header = ("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
			try {
				synchronized (clientOutput) {
					clientOutput.write(header);
					clientOutput.write(body);
					clientOutput.flush();
				}
			} catch (IOException ignored) {
				close();
			}
		}

		private void sendMessage(String jsonPayload) {
			if (jsonPayload == null || jsonPayload.isBlank()) return;
			try {
				wsContext.send(jsonPayload);
			} catch (Exception ignored) {
				close();
			}
		}

		private void close() {
			try {
				clientOutput.close();
			} catch (IOException ignored) {
			}
			try {
				serverInput.close();
			} catch (IOException ignored) {
			}
			try {
				serverOutput.close();
			} catch (IOException ignored) {
			}
		}
	}

	private static final class JsonRpcFrameOutputStream extends OutputStream {
		private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		private final java.util.function.Consumer<String> outboundMessageConsumer;

		private JsonRpcFrameOutputStream(java.util.function.Consumer<String> outboundMessageConsumer) {
			this.outboundMessageConsumer = outboundMessageConsumer;
		}

		@Override
		public synchronized void write(int b) throws IOException {
			buffer.write(b);
			drainFrames();
		}

		@Override
		public synchronized void write(byte[] b, int off, int len) throws IOException {
			buffer.write(b, off, len);
			drainFrames();
		}

		private void drainFrames() {
			byte[] bytes = buffer.toByteArray();
			int cursor = 0;
			while (cursor < bytes.length) {
				int headerEnd = indexOf(bytes, cursor, HEADER_SEPARATOR);
				if (headerEnd < 0) break;
				String headerText = new String(bytes, cursor, headerEnd - cursor, StandardCharsets.US_ASCII);
				int contentLength = parseContentLength(headerText);
				if (contentLength < 0) break;
				int bodyStart = headerEnd + HEADER_SEPARATOR.length;
				if (bytes.length - bodyStart < contentLength) break;
				String jsonPayload = new String(bytes, bodyStart, contentLength, StandardCharsets.UTF_8);
				outboundMessageConsumer.accept(jsonPayload);
				cursor = bodyStart + contentLength;
			}
			if (cursor > 0) {
				byte[] remaining = Arrays.copyOfRange(bytes, cursor, bytes.length);
				buffer.reset();
				buffer.write(remaining, 0, remaining.length);
			}
		}

		private static int parseContentLength(String headers) {
			String[] lines = headers.split("\\r\\n");
			for (String line : lines) {
				int separator = line.indexOf(':');
				if (separator <= 0) continue;
				String key = line.substring(0, separator).trim();
				if (!"Content-Length".equalsIgnoreCase(key)) continue;
				String value = line.substring(separator + 1).trim();
				try {
					return Integer.parseInt(value);
				} catch (NumberFormatException ignored) {
					return -1;
				}
			}
			return -1;
		}

		private static int indexOf(byte[] source, int offset, byte[] target) {
			if (target.length == 0) return offset;
			for (int i = offset; i <= source.length - target.length; i++) {
				boolean match = true;
				for (int j = 0; j < target.length; j++) {
					if (source[i + j] != target[j]) {
						match = false;
						break;
					}
				}
				if (match) return i;
			}
			return -1;
		}
	}
}
