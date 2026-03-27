package io.picota.backend.control.auth;

public interface GoogleIdentityVerifier {
	GoogleAuthConfig config();

	GoogleIdentity verify(String credential);
}
