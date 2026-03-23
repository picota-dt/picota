package io.picota.backend.control.ui;

public record VariableStat(
		Integer count,
		Double mean,
		Double std,
		Double min,
		Double max,
		Double median
) {
}
