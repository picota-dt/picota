package io.picota.language.compiler.codegeneration;

import io.intino.builder.CompilerConfiguration;
import io.intino.builder.OutputItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.intino.builder.BuildConstants.PRESENTABLE_MESSAGE;

public class Generator {
	protected final Map<String, List<String>> outMap = new LinkedHashMap<>();

	public Generator(CompilerConfiguration conf) {
		this.conf = conf;
	}

	protected final CompilerConfiguration conf;


	protected void put(String key, String value) {
		if (!outMap.containsKey(key)) outMap.put(key, new ArrayList<>());
		outMap.get(key).add(value);
	}

	protected List<OutputItem> toOutputList(Map<String, List<String>> outMap) {
		List<OutputItem> items = new ArrayList<>();
		outMap.keySet().forEach(key -> outMap.get(key).stream().map(value -> new OutputItem(key, value)).forEach(items::add));
		return items;
	}

	protected String prefix() {
		return PRESENTABLE_MESSAGE + "[" + conf.module() + " - " + conf.dsl().outDsl() + "]";
	}
}