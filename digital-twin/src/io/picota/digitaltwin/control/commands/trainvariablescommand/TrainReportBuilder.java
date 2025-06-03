package io.picota.digitaltwin.control.commands.trainvariablescommand;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import io.picota.digitaltwin.model.DigitalTwin.TrainingReport;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public class TrainReportBuilder {
	public void generate(TrainingReport report, File destination) throws IOException {
		PdfWriter writer = new PdfWriter(destination);
		PdfDocument pdf = new PdfDocument(writer);
		Document doc = new Document(pdf, PageSize.A4);
		doc.setMargins(36, 36, 36, 36);
		PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
		Paragraph title = new Paragraph("Digital Twin Training Report")
				.setFont(bold)
				.setFontSize(18)
				.setTextAlignment(TextAlignment.CENTER);
		doc.add(title);
		doc.add(new Paragraph("Status: " + report.state().name()).setMarginTop(12));
		if (!report.state().equals(Future.State.SUCCESS)) doc.add(new Paragraph("Report:\n " + report.report()).setMarginBottom(12));
		float[] colWidths = {2, 2, 2, 5};
		Table table = new Table(UnitValue.createPercentArray(colWidths)).useAllAvailableWidth();
		table.addHeaderCell(new Cell().add(new Paragraph("Subject")).setBackgroundColor(ColorConstants.LIGHT_GRAY));
		table.addHeaderCell(new Cell().add(new Paragraph("Variable")).setBackgroundColor(ColorConstants.LIGHT_GRAY));
		table.addHeaderCell(new Cell().add(new Paragraph("Loss")).setBackgroundColor(ColorConstants.LIGHT_GRAY));
		table.addHeaderCell(new Cell().add(new Paragraph("Contributors")).setBackgroundColor(ColorConstants.LIGHT_GRAY));
		for (TrainingReport.Variable t : report.trainings()) {
			table.addCell(new Cell().add(new Paragraph(t.dt())));
			table.addCell(new Cell().add(new Paragraph(t.name())));
			table.addCell(new Cell().add(new Paragraph(String.format("%.4f", t.loss()))));
			table.addCell(new Cell().add(new Paragraph(contributors(t))));
		}
		doc.add(table);
		doc.close();
	}

	private static String contributors(TrainingReport.Variable variable) {
		return variable.contributors().entrySet().stream()
				.sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
				.map(e -> e.getKey() + ": " + String.format("%.2f", e.getValue()))
				.collect(Collectors.joining("\n"));
	}
}
