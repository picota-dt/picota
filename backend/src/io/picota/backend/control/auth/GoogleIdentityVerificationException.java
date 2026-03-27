package io.picota.backend.control.auth;

public class GoogleIdentityVerificationException extends RuntimeException {
	public GoogleIdentityVerificationException(String message) {
		super(message);
	}

	public GoogleIdentityVerificationException(String message, Throwable cause) {
		super(message, cause);
	}
}
