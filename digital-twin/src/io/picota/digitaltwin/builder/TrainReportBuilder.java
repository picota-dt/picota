package io.picota.digitaltwin.builder;

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
import io.picota.digitaltwin.builder.DigitalSubjectBuilder.Result;

import java.io.File;
import java.io.IOException;

public class TrainReportBuilder {
	public void generate(Result result, File destination) throws IOException {
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
		doc.add(new Paragraph("Status: " + (result.statusCode() == 0 ? "Success" : "Failure")).setMarginTop(12));
		if (result.statusCode() != 0) doc.add(new Paragraph("Report:\n " + result.report()).setMarginBottom(12));
		float[] colWidths = {2, 4, 2, 4};
		Table table = new Table(UnitValue.createPercentArray(colWidths)).useAllAvailableWidth();
		table.addHeaderCell(new Cell().add(new Paragraph("DT")).setBackgroundColor(ColorConstants.LIGHT_GRAY));
		table.addHeaderCell(new Cell().add(new Paragraph("Variable")).setBackgroundColor(ColorConstants.LIGHT_GRAY));
		table.addHeaderCell(new Cell().add(new Paragraph("Loss")).setBackgroundColor(ColorConstants.LIGHT_GRAY));
		table.addHeaderCell(new Cell().add(new Paragraph("Contributors")).setBackgroundColor(ColorConstants.LIGHT_GRAY));
		for (Result.Training t : result.trainings()) {
			table.addCell(new Cell().add(new Paragraph(t.dt())));
			table.addCell(new Cell().add(new Paragraph(t.variable())));
			table.addCell(new Cell().add(new Paragraph(String.format("%.4f", t.loss()))));
			table.addCell(new Cell().add(new Paragraph(String.join(", ", t.contributors()))));
		}
		doc.add(table);
		doc.close();
	}
}
