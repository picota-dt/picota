package io.picota.dt.compiler;

import io.intino.builder.BuildConstants;
import io.intino.builder.OutputItem;
import io.intino.magritte.builder.StashBuilder;
import io.intino.tara.builder.CompilationInfoExtractor;
import io.intino.tara.builder.core.CompilerConfiguration;
import io.intino.tara.builder.core.errorcollection.TaraException;
import io.picota.dt.compiler.codegeneration.ScriptGenerationOperation;
import io.picota.dt.dsl.PicotaGraph;
import tara.dsl.Picota;

import java.io.File;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static io.intino.builder.BuildConstants.MESSAGES_END;
import static io.intino.builder.BuildConstants.MESSAGES_START;
import static io.intino.builder.BuildConstants.Mode.Build;


public class PicotacRunner {

	private static final Logger LOG = Logger.getGlobal();

	private PicotacRunner() {
	}

	public static void main(String[] args) {
		final boolean verbose = args.length != 2 || Boolean.parseBoolean(args[1]);
		if (verbose) System.out.println(BuildConstants.PRESENTABLE_MESSAGE + "Starting compiling");
		try {
			File argsFile;
			if (checkArgumentsNumber(args) || (argsFile = checkConfigurationFile(args[0])) == null)
				throw new TaraException("Error finding args file");
			final CompilerConfiguration config = new CompilerConfiguration();
			final Map<File, Boolean> sources = new LinkedHashMap<>();
			CompilationInfoExtractor.getInfoFromArgsFile(argsFile, config, sources);
			if (sources.isEmpty() || !config.mode().equals(Build)) return;
			PicotaGraph graph = loadGraph(sourcesMap(sources));
			if (graph == null) return;
			List<OutputItem> outputs = new ScriptGenerationOperation(config, graph).call();
			new CompilationReporter(config).report(sources, outputs);
		} catch (Exception e) {
			LOG.log(Level.SEVERE, e.getMessage() == null ? e.getStackTrace()[0].toString() : e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
	}

	private static Map<File, Charset> sourcesMap(Map<File, Boolean> sources) {
		return sources.keySet().stream().collect(Collectors.toMap(f -> f, f -> Charset.defaultCharset()));
	}

	private static PicotaGraph loadGraph(Map<File, Charset> map) {
		return PicotaGraph.load(new StashBuilder(map, new Picota(), "dt", System.out).build());
	}

	private static File checkConfigurationFile(String arg) {
		final File argsFile = new File(arg);
		if (!argsFile.exists()) {
			LOG.severe(MESSAGES_START + "Arguments file for Tara compiler not found" + MESSAGES_END);
			return null;
		}
		return argsFile;
	}

	private static boolean checkArgumentsNumber(String[] args) {
		if (args.length < 1) {
			LOG.severe(MESSAGES_START + "There is no arguments for tara compiler" + MESSAGES_END);
			return true;
		}
		return false;
	}
}