package io.picota.backend.control.auth;

public class StubGoogleIdentityVerifier implements GoogleIdentityVerifier {
	private final GoogleAuthConfig config;
	private final GoogleIdentity identity;

	public StubGoogleIdentityVerifier(GoogleAuthConfig config, GoogleIdentity identity) {
		this.config = config == null ? new GoogleAuthConfig("demo-google-client-id") : config;
		this.identity = identity == null
				? new GoogleIdentity("demo-google-subject", "alex.laurent@acme.io", true, "Alex Laurent")
				: identity;
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
		return identity;
	}
}
