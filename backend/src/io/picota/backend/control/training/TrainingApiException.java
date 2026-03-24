package io.picota.backend.control.training;

public class TrainingApiException extends RuntimeException {
	private final int statusCode;
	private final String responseBody;

	public TrainingApiException(String message) {
		this(message, 500, "", null);
	}

	public TrainingApiException(String message, Throwable cause) {
		this(message, 500, "", cause);
	}

	public TrainingApiException(String message, int statusCode, String responseBody) {
		this(message, statusCode, responseBody, null);
	}

	public TrainingApiException(String message, int statusCode, String responseBody, Throwable cause) {
		super(message, cause);
		this.statusCode = statusCode;
		this.responseBody = responseBody == null ? "" : responseBody;
	}

	public int statusCode() {
		return statusCode;
	}

	public String responseBody() {
		return responseBody;
	}
}
