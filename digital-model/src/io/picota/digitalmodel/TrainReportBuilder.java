package io.picota.digitalmodel;

import java.io.File;
import java.util.List;

public class TrainReportBuilder {
	private final List<DigitalTwinBuilder.Result.Training> trainings;
	private final String report;

	public TrainReportBuilder(List<DigitalTwinBuilder.Result.Training> trainings, String report) {
		this.trainings = trainings;
		this.report = report;
	}


	public void save(File destination) {

	}
}
