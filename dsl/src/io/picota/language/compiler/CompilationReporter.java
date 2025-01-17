package io.picota.language.compiler;

import io.intino.builder.CompilerMessage;
import io.intino.builder.OutputItem;
import io.intino.tara.builder.core.CompilerConfiguration;

import java.io.File;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;

public class CompilationReporter {
	private final CompilerConfiguration config;
	private final PrintStream out;

	public CompilationReporter(CompilerConfiguration config) {
		this.config = config;
		this.out = config.out();
	}

	void report(Map<File, Boolean> srcFiles, List<OutputItem> compiled) {
		if (compiled.isEmpty()) {
			this.reportNotCompiledItems(srcFiles);
		} else {
			this.reportCompiledItems(compiled);
		}
		this.out.println();
	}

	private void printMessage(CompilerMessage message) {
		this.out.print("%%m");
		this.out.print(message.category());
		this.out.print("#%%#%%%#%%%%%%%%%#");
		this.out.print(message.message());
		this.out.print("#%%#%%%#%%%%%%%%%#");
		this.out.print(message.url());
		this.out.print("#%%#%%%#%%%%%%%%%#");
		this.out.print(message.lineNum());
		this.out.print("#%%#%%%#%%%%%%%%%#");
		this.out.print(message.columnNum());
		this.out.print("#%%#%%%#%%%%%%%%%#");
		this.out.print("/%m");
		this.out.println();
	}

	private void reportCompiledItems(List<OutputItem> compiledFiles) {
		for(OutputItem compiledFile : compiledFiles) {
			this.out.print("%%c");
			this.out.print(compiledFile.getOutputPath());
			this.out.print("#%%#%%%#%%%%%%%%%#");
			this.out.print(compiledFile.getSourcePath());
			this.out.print("/%c");
			this.out.println();
		}

	}

	private void reportNotCompiledItems(Map<File, Boolean> toRecompile) {
		for(File file : toRecompile.keySet()) {
			this.out.print("%%rc");
			this.out.print(file.getAbsolutePath());
			this.out.print("/%rc");
			this.out.println();
		}

	}
}
