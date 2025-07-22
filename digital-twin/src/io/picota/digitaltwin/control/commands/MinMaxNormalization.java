package io.picota.digitaltwin.control.commands;

import systems.intino.datamarts.subjectstore.calculator.model.Filter;

public class MinMaxNormalization implements Filter {
	private final double min;
	private final double max;

	public MinMaxNormalization(double min, double max) {
		this.min = min;
		this.max = max;
	}

	@Override
	public double[] apply(double[] input) {
		double[] output = new double[input.length];
		double range = max - min;
		for (int i = 0; i < input.length; i++)
			output[i] = range != 0 ? (input[i] - min) / range : 0;
		return output;
	}
}
