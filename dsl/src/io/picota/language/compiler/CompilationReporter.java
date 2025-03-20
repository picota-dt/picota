package io.picota.language.compiler;

import io.intino.builder.CompilerConfiguration;
import io.intino.builder.CompilerMessage;
import io.intino.builder.OutputItem;

import java.io.File;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;

public class CompilationReporter {
	private final PrintStream out;

	public CompilationReporter(CompilerConfiguration config) {
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
