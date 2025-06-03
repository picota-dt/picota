package io.picota.digitaltwin;

public class DigitalTwinServiceAuthenticator {
	private DigitalTwinBox box;

	public DigitalTwinServiceAuthenticator(DigitalTwinBox box) {
		this.box = box;
	}

	public boolean isAuthenticated(String token) {
		return token.trim().equals("b0fWZn5p95YGiEhI2EfJxKpfOqEEY5sD1MChTmO12Ra6ao0OkJ2zEZ4pj0xNibqOUIEYfCr3lGA30AQxo3Aa4SFP2fz4k93FIDXVVkimJZzkjbffkgVXeFcepysJmkbu");
	}
}