package io.picota.backend.control.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

public class GoogleIdTokenVerifierService implements GoogleIdentityVerifier {
	private final GoogleAuthConfig config;
	private final GoogleIdTokenVerifier verifier;

	public GoogleIdTokenVerifierService(GoogleAuthConfig config) {
		this.config = config == null ? new GoogleAuthConfig("") : config;
		if (!this.config.configured()) {
			throw new IllegalArgumentException("Google client id is required");
		}
		this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
				.setAudience(Collections.singletonList(this.config.clientId()))
				.build();
	}

	@Override
	public GoogleAuthConfig config() {
		return config;
	}

	@Override
	public GoogleIdentity verify(String credential) {
		String rawCredential = credential == null ? "" : credential.trim();
		if (rawCredential.isBlank()) {
			throw new GoogleIdentityVerificationException("Google credential is required");
		}
		try {
			GoogleIdToken idToken = verifier.verify(rawCredential);
			if (idToken == null) {
				throw new GoogleIdentityVerificationException("Invalid Google ID token");
			}
			GoogleIdToken.Payload payload = idToken.getPayload();
			String subject = payload.getSubject();
			String email = payload.getEmail();
			boolean emailVerified = Boolean.TRUE.equals(payload.getEmailVerified());
			String name = payload.containsKey("name") ? String.valueOf(payload.get("name")) : email;
			if (subject == null || subject.isBlank()) {
				throw new GoogleIdentityVerificationException("Google ID token does not contain a subject");
			}
			if (email == null || email.isBlank()) {
				throw new GoogleIdentityVerificationException("Google account email is missing");
			}
			if (!emailVerified) {
				throw new GoogleIdentityVerificationException("Google account email is not verified");
			}
			return new GoogleIdentity(subject.trim(), email.trim(), true, name == null ? email.trim() : name.trim());
		} catch (GoogleIdentityVerificationException e) {
			throw e;
		} catch (GeneralSecurityException | IOException e) {
			throw new GoogleIdentityVerificationException("Unable to verify Google ID token", e);
		} catch (RuntimeException e) {
			throw new GoogleIdentityVerificationException("Invalid Google ID token", e);
		}
	}
}
