package io.picota.digitaltwin.control.commands.trainvariablescommand;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceGray;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.AreaBreakType;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static com.itextpdf.layout.borders.Border.NO_BORDER;
import static com.itextpdf.layout.properties.TextAlignment.LEFT;
import static com.itextpdf.layout.properties.TextAlignment.RIGHT;

public class TrainReportBuilder {
	private final PdfFont headerFont;
	private final PdfFont labelFont;
	private final PdfFont valueFont;
	private final DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	public TrainReportBuilder() throws IOException {
		headerFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
		labelFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);
		valueFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
	}

	public void generate(DataSheetReport report, File dest) throws IOException {
		PdfDocument pdf = new PdfDocument(new PdfWriter(dest));
		Document doc = new Document(pdf, PageSize.A4.rotate());
		doc.setMargins(36, 36, 36, 36);
		List<TrainedSubject> subjects = report.subjects();
		for (int s = 0; s < subjects.size(); s++) {
			TrainedSubject subject = subjects.get(s);
			List<Inference> inferences = subject.estimations();
			int total = inferences.size();
			int pages = (total + 3) / 4;
			for (int p = 0; p < pages; p++) {
				if (!(s == 0 && p == 0)) doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
				doc.add(header(report));
				doc.add(new Paragraph()
						.add(new Text("Digital Subject: ").setFont(labelFont).setFontSize(18))
						.add(new Text(subject.subject()).setFont(valueFont).setFontSize(18))
						.setMarginBottom(2));
				Table grid = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
						.useAllAvailableWidth()
						.setMarginBottom(2)
						.setHorizontalAlignment(HorizontalAlignment.CENTER);

				for (int cell = 0; cell < 4; cell++) {
					int idx = p * 4 + cell;
					Cell gridCell = new Cell()
							.setBorder(Border.NO_BORDER)
							.setPadding(12)
							.setVerticalAlignment(VerticalAlignment.TOP);
					if (idx < total) {
						gridCell.add(renderEstimations(inferences.get(idx)));
					}
					grid.addCell(gridCell);
				}
				doc.add(grid);
			}
		}
		doc.close();
	}

	private Div renderEstimations(Inference inf) {
		Div content = new Div();
		Paragraph paragraph = new Paragraph()
				.add(new Text((inf.horizon > 0 ? "Prediction" : "Estimation") + ": ").setFont(labelFont).setFontSize(16))
				.add(new Text(inf.variable()).setFont(valueFont).setFontSize(16));
		if (!inf.unit.isEmpty())
			paragraph.add(new Text(" (")).addAll(format(inf.unit())).add(new Text(")").setFont(valueFont).setFontSize(16));
		if (inf.horizon > 0)
			paragraph.add(new Text(" - Time Horizon " + inf.horizon()).setFont(valueFont).setFontSize(16));
		content.add(paragraph);
		content.add(new Paragraph()
				.add(new Text("Inference Error: ±").setFont(labelFont).setFontSize(12))
				.add(new Text(inf.error() + " %").setFont(valueFont).setFontSize(12))
				.setMarginBottom(4));
		content.add(new Paragraph("Top Contributing Inputs")
				.setFont(labelFont).setFontSize(12).setMarginBottom(4));
		Table t = new Table(UnitValue.createPercentArray(new float[]{3, 2})).setPadding(20)
				.useAllAvailableWidth();
		SolidBorder border = new SolidBorder(new DeviceGray(0.7f), 0.5f);
		DeviceRgb headerBg = new DeviceRgb(240, 240, 240);
		t.addHeaderCell(new Cell().setBorder(border)
				.add(new Paragraph("Input Variable").setFont(valueFont).setFontSize(11))
				.setBackgroundColor(headerBg).setTextAlignment(LEFT));
		t.addHeaderCell(new Cell().setBorder(border)
				.add(new Paragraph("Weight (%)").setFont(valueFont).setFontSize(11))
				.setBackgroundColor(headerBg).setTextAlignment(RIGHT));
		inf.contributors().entrySet().stream()
				.sorted(Comparator.comparingDouble(Map.Entry<String, Double>::getValue).reversed())
				.limit(4)
				.forEach(e -> {
					t.addCell(new Cell().setBorder(border)
							.add(new Paragraph(e.getKey()).setFont(labelFont).setFontSize(11)));
					t.addCell(new Cell().setBorder(border)
							.add(new Paragraph(String.format("%.2f", e.getValue() * 100) + "%").setFont(labelFont).setFontSize(11))
							.setTextAlignment(RIGHT));
				});
		content.add(t);
		return content;
	}

	private List<Text> format(String unit) {
		String[] split = unit.split("(?=\\^[+-]?\\d+)");
		return Arrays.stream(split).map(text -> text.contains("^") ? superIndex(text) : new Text(text)).toList();
	}

	private Text superIndex(String text) {
		return new Text(text.replace("^", "")).setTextRise(6);
	}

	private Div header(DataSheetReport report) throws IOException {
		return new Div().add(topBar())
				.add(headerFirstLine(report))
				.add(titleAndRelease(report))
				.add(new LineSeparator(new SolidLine(1f)).setMarginBottom(8));
	}

	private Table headerFirstLine(DataSheetReport report) {
		return new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth().setMarginTop(4).setPaddingBottom(0).setMarginBottom(0)
				.addCell(new Cell().add(new Paragraph().add(new Text("Model Identifier: ").setFont(labelFont).setFontSize(10)).add(new Text(report.getModelId()).setFont(valueFont).setFontSize(10))).setBorder(NO_BORDER))
				.addCell(new Cell().add(new Paragraph(report.url()).setFont(labelFont).setFontSize(10).setTextAlignment(RIGHT)).setBorder(NO_BORDER));
	}

	private Table titleAndRelease(DataSheetReport report) {
		return new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth().setMarginTop(0).setPaddingTop(0).setMarginBottom(4)
				.addCell(new Cell().add(new Paragraph()
						.add(new Text("Title: ").setFont(labelFont).setFontSize(10))
						.add(new Text(report.title()).setFont(valueFont).setFontSize(10))
						.add(new Text("   Release Date: ").setFont(labelFont).setFontSize(10))
						.add(new Text(formatDate(report.releaseDate)).setFont(valueFont).setFontSize(10))).setBorder(NO_BORDER));
	}

	private Table topBar() throws IOException {
		Table bar = new Table(UnitValue.createPercentArray(new float[]{8, 1}))
				.useAllAvailableWidth()
				.setBackgroundColor(ColorConstants.BLACK);
		bar.addCell(new Cell()
				.add(new Paragraph("Inferential DataSheet")
						.setFont(headerFont).setFontSize(18).setFontColor(ColorConstants.WHITE))
				.setBorder(NO_BORDER)
				.setVerticalAlignment(VerticalAlignment.MIDDLE)
				.setPadding(8));
		Table inner = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
				.useAllAvailableWidth()
				.setBorder(NO_BORDER)
				.addCell(new Cell()
						.add(getLogo())
						.setBorder(NO_BORDER)
						.setVerticalAlignment(VerticalAlignment.MIDDLE)
						.setPadding(0))
				.addCell(new Cell()
						.add(new Paragraph("picota")
								.setFont(labelFont).setFontSize(12).setFontColor(ColorConstants.WHITE)
								.setTextAlignment(RIGHT))
						.setBorder(NO_BORDER)
						.setVerticalAlignment(VerticalAlignment.BOTTOM)
						.setPadding(8));
		Cell right = new Cell()
				.add(inner)
				.setHorizontalAlignment(HorizontalAlignment.RIGHT)
				.setBorder(NO_BORDER)
				.setPadding(0);
		bar.addCell(right);
		return bar;
	}

	private String formatDate(Instant date) {
		return date.atOffset(ZoneOffset.UTC).format(dateFmt);
	}

	private Image getLogo() throws IOException {
		ImageData img = ImageDataFactory.create(this.getClass().getResourceAsStream("/www/images/logo.png").readAllBytes());
		Image logo = new Image(img).scaleToFit(40, 40);
		return logo;
	}

	public record DataSheetReport(String getModelId, String title, Instant releaseDate, String url,
								  List<TrainedSubject> subjects) {
	}

	public record TrainedSubject(String subject, List<Inference> estimations) {
	}

	public record Inference(String variable, int horizon, String unit, String error, Map<String, Double> contributors) {
	}
}
