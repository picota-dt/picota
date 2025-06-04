package io.picota.digitaltwin;

import java.io.IOException;

public class DigitaltwinServiceAuthenticator {
	private String token;
	private DigitalTwinBox box;

	public DigitaltwinServiceAuthenticator(DigitalTwinBox box) {
		try {
			this.box = box;
			token = new String(this.getClass().getResourceAsStream("/API_TOKEN.txt").readAllBytes());
		} catch (IOException e) {
			token = "";
		}
	}

	public boolean isAuthenticated(String token) {
		return token.trim().equals(this.token);
	}
}