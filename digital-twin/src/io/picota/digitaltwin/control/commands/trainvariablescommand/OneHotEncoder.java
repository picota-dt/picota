package io.picota.digitaltwin.control.commands.trainvariablescommand;

import io.quassar.monentia.picota.Variable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class OneHotEncoder {
	private final File file;
	private final List<String> header;
	private final Map<String, Variable> variables;
	private final Map<String, List<String>> categories;
	private final Path tempFile;
	private final Map<Integer, List<String>> categoriesByIndex;
	private final List<Integer> nonCatIndices;

	public OneHotEncoder(File file, List<String> header, Map<String, Variable> variables) throws IOException {
		this.file = file;
		this.tempFile = Files.createTempFile(file.getParentFile().toPath(), "one_hot", ".tsv");
		this.header = header;
		this.variables = variables;
		this.categories = categoricalVariables();
		this.categoriesByIndex = new LinkedHashMap<>();
		this.nonCatIndices = new ArrayList<>();
		fillIndices();
	}

	public List<String> encode() throws IOException {
		List<String> newHeader = nonCatIndices.stream().mapToInt(idx -> idx).mapToObj(header::get).collect(Collectors.toList());
		try (BufferedReader br = new BufferedReader(new FileReader(file)); BufferedWriter bw = new BufferedWriter(new FileWriter(tempFile.toFile()))) {
			br.readLine();
			expandCategories(newHeader, bw);
			String line;
			while ((line = br.readLine()) != null)
				bw.write(String.join("\t", compoundNewFields(line, newHeader)) + "\n");
		}
		Files.move(tempFile, file.toPath(), REPLACE_EXISTING);
		return newHeader;
	}

	private List<String> compoundNewFields(String line, List<String> newHeader) {
		String[] fields = line.split("\t", -1);
		List<String> outFields = new ArrayList<>(newHeader.size());
		for (int idx : nonCatIndices) outFields.add(fields[idx]);
		for (List<String> cats : categoriesByIndex.values()) {
			String val = fields[categoriesByIndex.keySet()
					.stream()
					.filter(i -> categoriesByIndex.get(i) == cats)
					.findFirst()
					.get()];
			for (String cat : cats) outFields.add(val.equals(cat) ? "1" : "0");
		}
		return outFields;
	}

	private void expandCategories(List<String> newHeader, BufferedWriter bw) throws IOException {
		for (Map.Entry<Integer, List<String>> e : categoriesByIndex.entrySet())
			e.getValue().stream().map(cat -> header.get(e.getKey()) + "_" + cat).forEach(newHeader::add);
		bw.write(String.join("\t", newHeader));
		bw.newLine();
	}

	private void fillIndices() {
		IntStream.range(0, header.size()).forEach(i -> {
			String colName = header.get(i);
			if (categories.containsKey(colName)) categoriesByIndex.put(i, categories.get(colName));
			else nonCatIndices.add(i);
		});
	}

	private Map<String, List<String>> categoricalVariables() {
		return this.variables.entrySet().stream().filter(e -> !e.getValue().isNumeric()).collect(Collectors.toMap(Map.Entry::getKey, e -> getValues(e.getValue())));
	}

	private static List<String> getValues(Variable e) {
		return e.isEnumerated() ? e.asEnumerated().values() : List.of("true", "false");
	}
}
