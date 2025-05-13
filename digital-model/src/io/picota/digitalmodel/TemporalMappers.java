package io.picota.digitalmodel;

import java.util.HashMap;
import java.util.Map;

public class TemporalMappers {

	public static final String[] temporalFields = new String[]{
			"month_sin", "month_cos",
			"day_sin", "day_cos",
			"hour_sin", "hour_cos",
			"day_of_year_sin", "day_of_year_cos",
			"week_of_year_sin", "week_of_year_cos",
			"quarter_sin", "quarter_cos",
			"season_sin", "season_cos"};

	public static Map<String, String> mappers = new HashMap<>();

	static {
		mappers.put("month_sin", "sin(ts.month)");
		mappers.put("month_cos", "cos(ts.month)");
		mappers.put("day_sin", "sin(ts.year-month-day)");
		mappers.put("day_cos", "cos(ts.year-month-day)");
		mappers.put("hour_sin", "sin(ts.year-month-day-hour)");
		mappers.put("hour_cos", "cos(ts.year-month-day-hour)");
		mappers.put("quarter_sin", "sin(ts.year-quarter)");
		mappers.put("quarter_cos", "cos(ts.year-quarter)");
	}
}
