package io.picota.digitaltwin.control.commands.trainvariablescommand;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.AreaBreakType;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import io.picota.digitaltwin.model.DigitalTwin.TrainingReport;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TrainReportBuilder {

	public void generate(TrainingReport report, File destination) throws IOException {
		PdfWriter writer = new PdfWriter(destination);
		PdfDocument pdf = new PdfDocument(writer);
		Document doc = new Document(pdf, PageSize.A4);
		doc.setMargins(36, 36, 36, 36);

		PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
		PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);

		addCoverPage(doc, bold, regular, report.state().name());

		Map<String, List<TrainingReport.Variable>> subjects = report.trainings()
				.stream()
				.collect(Collectors.groupingBy(TrainingReport.Variable::dt));

		for (String subject : subjects.keySet()) {
			doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
			doc.add(new Paragraph("Subject: " + subject)
					.setFont(bold)
					.setFontSize(14)
					.setFontColor(ColorConstants.BLUE)
					.setTextAlignment(TextAlignment.LEFT)
					.setMarginBottom(8));

			Table table = createVariablesTable(bold);
			subjects.get(subject).forEach(v -> {
				table.addCell(new Cell().add(new Paragraph(v.name())).setFont(regular));
				table.addCell(new Cell().add(new Paragraph(String.format("%.4f", v.loss()))).setFont(regular));
				table.addCell(new Cell().add(new Paragraph(contributors(v))).setFont(regular));
			});
			doc.add(table);
		}

		doc.close();
	}

	private void addCoverPage(Document doc, PdfFont bold, PdfFont regular, String state) throws IOException {
		ImageData imgData = ImageDataFactory.create(
				this.getClass().getResourceAsStream("/www/images/logo.png").readAllBytes()
		);
		doc.add(new Image(imgData)
				.scaleToFit(150, 150)
				.setHorizontalAlignment(HorizontalAlignment.CENTER)
				.setMarginBottom(20));
		doc.add(new Paragraph("Digital Twin Training Report")
				.setFont(bold)
				.setFontSize(24)
				.setTextAlignment(TextAlignment.CENTER)
				.setMarginBottom(5));
		String today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy"));
		doc.add(new Paragraph("Date of creation: " + today)
				.setFont(regular)
				.setFontSize(12)
				.setTextAlignment(TextAlignment.CENTER)
				.setMarginBottom(5));
		doc.add(new Paragraph("State: " + state)
				.setFont(bold)
				.setFontSize(12)
				.setTextAlignment(TextAlignment.CENTER)
				.setMarginBottom(10));
	}

	private Table createVariablesTable(PdfFont bold) {
		float[] colWidths = {3, 2, 5};
		Table table = new Table(UnitValue.createPercentArray(colWidths))
				.useAllAvailableWidth()
				.setMarginTop(10);

		String[] headers = {"Variable", "Loss", "Contributors"};
		for (String h : headers) {
			table.addHeaderCell(new Cell()
					.add(new Paragraph(h).setFont(bold))
					.setBackgroundColor(ColorConstants.LIGHT_GRAY)
					.setTextAlignment(TextAlignment.CENTER));
		}
		return table;
	}

	private static String contributors(TrainingReport.Variable variable) {
		return variable.contributors().entrySet().stream()
				.sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
				.map(e -> e.getKey() + ": " + String.format("%.2f", e.getValue()))
				.collect(Collectors.joining("\n"));
	}
}
