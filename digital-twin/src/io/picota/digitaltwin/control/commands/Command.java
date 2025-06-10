package io.picota.digitaltwin.control.commands;

public interface Command<T> {
	Result<T> execute();

	static Result<Void> success() {
		return new Result(true, "", null);
	}

	static Result<?> success(Object resource) {
		return new Result(true, "", resource);
	}

	record Result<T>(boolean success, String remarks, T resource) {
		public Result(boolean success, String remarks) {
			this(success, remarks, null);
		}
	}
}