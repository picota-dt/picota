package io.picota.digitaltwin.control.commands;

public interface Command {
	static Result success() {
		return new Result(true, "", null);
	}

	static Result success(Object resource) {
		return new Result(true, "", resource);
	}

	record Result(boolean success, String remarks, Object resource) {

		public Result(boolean success, String remarks) {
			this(success, remarks, null);
		}
	}

	Result execute();
}