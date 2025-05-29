package io.picota.digitaltwin.utils;

import io.quassar.picota.DigitalTwin.DigitalSubject.Resolution.Scale;

import java.io.File;
import java.time.Duration;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.time.temporal.ChronoUnit.*;

public class Utils {
	public static TemporalAmount periodOf(int amount, ChronoUnit unit) {
		return switch (unit) {
			case YEARS -> Period.ofYears(amount);
			case MONTHS -> Period.ofMonths(amount);
			case DAYS -> Period.ofDays(amount);
			case HOURS -> Period.ofDays(amount);
			case MINUTES -> Duration.ofMinutes(amount);
			default -> throw new IllegalArgumentException("Unsupported unit: " + unit);
		};
	}

	public static ChronoUnit chronoUnitOf(Scale scale) {
		return switch (scale) {
			case Years -> YEARS;
			case Months -> MONTHS;
			case Days -> DAYS;
			case Hours -> HOURS;
			case Minutes -> MINUTES;
			case Seconds -> SECONDS;
		};
	}

	public static List<File> getFilesWithPrefix(File dir, String prefix) {
		List<File> result = new ArrayList<>();
		if (dir != null && dir.isDirectory()) {
			File[] files = dir.listFiles((d, name) -> name.startsWith(prefix));
			if (files != null) {
				result.addAll(Arrays.asList(files));
			}
		}
		return result;
	}
}
