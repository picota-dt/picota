package io.picota.digitaltwin.control.commands.trainvariablescommand;

import systems.intino.datamarts.subjectstore.calculator.model.filters.CosFilter;
import systems.intino.datamarts.subjectstore.calculator.model.filters.SinFilter;
import systems.intino.datamarts.subjectstore.view.history.format.ColumnDefinition;

import java.util.List;
import java.util.Set;

public class TemporalColumns {
	private static final Set<String> temporalColumns = Set.of("month_sin", "month_cos", "day_sin", "day_cos", "hour_sin", "hour_cos", "quarter_sin", "quarter_cos");

	public static List<ColumnDefinition> get() {
		return List.of(
				new ColumnDefinition("month_sin", "ts.month-of-year").add(new SinFilter()),
				new ColumnDefinition("month_cos", "ts.month-of-year").add(new CosFilter()),
				new ColumnDefinition("day_sin", "ts.day-of-month").add(new SinFilter()),
				new ColumnDefinition("day_cos", "ts.day-of-month").add(new CosFilter()),
				new ColumnDefinition("hour_sin", "ts.hour-of-day").add(new SinFilter()),
				new ColumnDefinition("hour_cos", "ts.hour-of-day").add(new CosFilter()),
				new ColumnDefinition("quarter_sin", "ts.quarter-of-year").add(new SinFilter()),
				new ColumnDefinition("quarter_cos", "ts.quarter-of-year").add(new CosFilter())
//				new ColumnDefinition("ts-day", "ts.day-of-month"),
//				new ColumnDefinition("ts-hour", "ts.hour-of-day")
		);
	}

	public static boolean is(String v) {
		return temporalColumns.contains(v);
	}
}
