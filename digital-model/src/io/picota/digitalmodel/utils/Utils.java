package io.picota.digitalmodel.utils;

import model.DigitalTwin;

import java.time.Period;
import java.time.temporal.ChronoUnit;

import static java.time.temporal.ChronoUnit.*;
import static java.time.temporal.ChronoUnit.HOURS;
import static java.time.temporal.ChronoUnit.MINUTES;

public class Utils {
	public static Period periodOf(int amount, ChronoUnit unit) {
		return switch (unit) {
			case YEARS -> Period.ofYears(amount);
			case MONTHS -> Period.ofMonths(amount);
			case DAYS -> Period.ofDays(amount);
			default -> throw new IllegalArgumentException("Unsupported unit: " + unit);
		};
	}

	public static ChronoUnit chronoUnitOf(DigitalTwin.Resolution.Scale scale) {
		return switch (scale) {
			case Year -> YEARS;
			case Month -> MONTHS;
			case Day -> DAYS;
			case Hour -> HOURS;
			case Minute -> MINUTES;
		};
	}
}
