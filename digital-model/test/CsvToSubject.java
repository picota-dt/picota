import org.jetbrains.annotations.NotNull;
import systems.intino.datamarts.subjectstore.SubjectHistory;
import systems.intino.datamarts.subjectstore.SubjectStore;
import systems.intino.datamarts.subjectstore.model.Subject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CsvToSubject {

	public static void main(String[] args) throws IOException {
		SubjectStore subjectStore = new SubjectStore("jdbc:sqlite:" + new File("digital-model/test-res/infecar.ddb").getAbsolutePath());
		Subject subject = subjectStore.create(DTTest.DIGITAL_TWIN_NAME);
		File source = new File("digital-model/test-res/infecar.csv");
		List<String> columns = getColumns(source);
		try (SubjectHistory history = subject.history()) {
			SubjectHistory.Batch batch = history.batch();
			Files.lines(source.toPath()).skip(1).map(l -> l.split(",")).forEach(f -> {
				SubjectHistory.Transaction t = batch.on(Instant.parse(f[0]), DTTest.DIGITAL_TWIN_NAME);
				for (int i = 1; i < f.length; i++) t.put(columns.get(i), Double.valueOf(f[i]));
				t.terminate();
			});
			batch.terminate();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		subjectStore.close();
	}

	@NotNull
	private static List<String> getColumns(File source) throws IOException {
		String header = Files.lines(source.toPath()).findFirst().get();
		List<String> columns = new ArrayList<>();
		Collections.addAll(columns, header.split(","));
		return columns;
	}
}
