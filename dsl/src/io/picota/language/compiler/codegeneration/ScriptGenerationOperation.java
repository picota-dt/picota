package io.picota.language.compiler.codegeneration;

import com.esotericsoftware.kryo.io.Input;
import io.intino.builder.OutputItem;
import io.intino.itrules.Engine;
import io.intino.itrules.Frame;
import io.intino.itrules.FrameBuilder;
import io.intino.itrules.template.Template;
import io.intino.magritte.framework.Layer;
import io.intino.tara.builder.core.CompilerConfiguration;
import io.intino.tara.builder.core.errorcollection.TaraException;
import io.picota.language.compiler.util.TarExtractor;
import io.picota.language.model.DigitalTwin;
import io.picota.language.model.PicotaGraph;
import io.picota.language.model.Variable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static java.util.logging.Level.SEVERE;

@SuppressWarnings("resource")
public class ScriptGenerationOperation extends Generator {
	private static final Logger LOG = Logger.getGlobal();
	private final Map<File, Boolean> sources;
	private final PicotaGraph model;
	private final File outFolder;
	private final Template template;
	private final Map<String, List<String>> outMap = new LinkedHashMap<>();

	public ScriptGenerationOperation(CompilerConfiguration conf, Map<File, Boolean> sources, PicotaGraph model) {
		super(conf);
		this.outFolder = conf.getOutDirectory();
		this.sources = sources;
		this.model = model;
		this.template = new ScriptTemplate();
	}

	public List<OutputItem> call() throws TaraException {
		try {
			if (conf.isVerbose()) conf.out().println(prefix() + " Generating Script...");
			createScripts();
			copyLibs();
			return toOutputList(outMap);
		} catch (Throwable e) {
			LOG.log(SEVERE, "Error during script generation: " + e.getMessage(), e);
			throw new TaraException(e.getMessage());
		}
	}

	private void copyLibs() throws IOException {
		TarExtractor.extractTarFile(this.getClass().getResourceAsStream("/libs.tar"), outFolder);
	}

	private void createScripts() throws IOException {
		for (var dt : model.digitalTwinList()) {
			File dir = new File(outFolder, dt.po().name$().toLowerCase() + "_" + dt.name$());
			dir.mkdirs();
			renderInterface(dt, dir);
		}
	}

	private void renderInterface(DigitalTwin dt, File dir) throws IOException {
		for (DigitalTwin.Interface i : dt.interfaceList()) {
			File file = new File(dir, i.physicVariable().name$() + ".py");
			Files.writeString(file.toPath(), new Engine(template).render(frameOf(dt, i)));
			put(sources.keySet().iterator().next().getAbsolutePath(), file.getAbsolutePath());
		}
	}

	private Frame frameOf(DigitalTwin dt, DigitalTwin.Interface i) {
		return new FrameBuilder("variable")
				.add("variables", i.causedBy().stream().map(ScriptGenerationOperation::frameOfVariable).toArray())
				.add("output", i.physicVariable().name$())
				.add("cyclicVariables", cyclicVariables(i))
				.add("normalized", i.causedBy().stream().filter(Variable::isNumeric).map(Layer::name$).toArray())
				.toFrame();
	}

	private Object[] cyclicVariables(DigitalTwin.Interface i) {
		return i.causedBy().stream().filter(Variable::isCyclic).map(v -> v.a$(Variable.Cyclic.class)).map(this::frameOf).toArray();
	}

	private static FrameBuilder frameOfVariable(Variable v) {
		return new FrameBuilder("variable", v.isCyclic() ? "cyclic" : "regular").add("name", v.name$());
	}

	private Frame frameOf(Variable.Cyclic c) {
		return new FrameBuilder("variable", "cyclic")
				.add("name", c.name$())
				.add("cycle", c.cycle())
				.toFrame();
	}
}