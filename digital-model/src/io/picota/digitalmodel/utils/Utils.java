package io.picota.digitalmodel.utils;

import java.time.Period;
import java.time.temporal.ChronoUnit;

public class Utils {
	public static Period periodOf(int amount, ChronoUnit unit) {
		return switch (unit) {
			case YEARS -> Period.ofYears(amount);
			case MONTHS -> Period.ofMonths(amount);
			case DAYS -> Period.ofDays(amount);
			default -> throw new IllegalArgumentException("Unsupported unit: " + unit);
		};
	}
}
