package io.picota.language.compiler.codegeneration;

import io.intino.builder.CompilerConfiguration;
import io.intino.builder.OutputItem;
import io.intino.itrules.Frame;
import io.intino.itrules.FrameBuilder;
import io.intino.tara.builder.core.errorcollection.TaraException;
import io.picota.language.model.*;
import io.picota.language.model.rules.Scale;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static java.util.logging.Level.SEVERE;

@SuppressWarnings("resource")
public class PicotaSetupGenerationOperation extends Generator {
	private static final Logger LOG = Logger.getGlobal();
	private final File outFolder;
	private final File srcFolder;
	private final Map<File, Boolean> sources;
	private final PicotaGraph model;

	public PicotaSetupGenerationOperation(CompilerConfiguration conf, Map<File, Boolean> sources, PicotaGraph model) {
		super(conf);
		this.outFolder = conf.genDirectory();
		this.srcFolder = conf.srcDirectory();
		this.sources = sources;
		this.model = model;
		sources.keySet().forEach(k -> outMap.put(k.getAbsolutePath(), new ArrayList<>()));
	}

	public List<OutputItem> call() throws TaraException {
		try {
			if (conf.isVerbose()) conf.out().println(prefix() + " Generating Digital Twin Infrastructure...");
			graphLoader();
			main();
			return toOutputList(outMap);
		} catch (Throwable e) {
			LOG.log(SEVERE, "Error during generation: " + e.getMessage(), e);
			throw new TaraException(e.getMessage());
		}
	}

	private void main() throws IOException {
		File dir = new File(srcFolder, conf.generationPackage().replace(".", File.separator));
		File file = new File(dir, "Main.java");
		if (file.exists()) return;
		dir.mkdirs();
		String output = new PicotaSetupTemplate().render(new FrameBuilder("setup").add("package", conf.generationPackage()));
		Files.writeString(file.toPath(), output);
	}

	private void graphLoader() throws IOException {
		Frame[] digitalTwins = model.digitalTwinList().stream().map(this::frameOf).toArray(Frame[]::new);
		Frame frame = new FrameBuilder("graphloader")
				.add("package", conf.generationPackage())
				.add("scale", "Hour")
				.add("digitalTwin", digitalTwins).toFrame();
		String content = new GraphLoaderTemplate().render(frame);
		File dir = new File(outFolder, conf.generationPackage().replace(".", File.separator));
		dir.mkdirs();
		File target = new File(dir, "GraphLoader.java");
		Files.writeString(target.toPath(), content);
		sources.keySet().forEach(file -> outMap.get(file.getAbsolutePath()).add(target.getAbsolutePath()));

	}

	private Frame frameOf(DigitalTwin digitalTwin) {
		FrameBuilder builder = new FrameBuilder("digitalTwin").add("name", digitalTwin.name$());
		List<Frame> variables = new ArrayList<>();
		if (digitalTwin.entity() != null) {
			Reality reality = digitalTwin.entity().core$().ownerAs(Reality.class);
			List<ViewPoint> viewPoints = reality.viewPointList();
			variables.addAll(viewPoints.stream().flatMap(vp -> vp.variableList().stream())
					.map(this::frameOf).toList());
			List<ViewPoint> entityVP = digitalTwin.entity().viewPointList();
			variables.addAll(entityVP.stream().flatMap(vp -> vp.variableList().stream()).map(this::frameOf).toList());
		}
		Duration duration = Duration.of(digitalTwin.resolution().value(), digitalTwin.resolution().scale().chronoUnit());
		builder.add("period", duration.toMinutes()).add("scale", Scale.Minute.name());
		builder.add("variable", variables.toArray(new Frame[0]));
		return builder.toFrame();
	}

	private Frame frameOf(Variable v) {
		FrameBuilder builder = new FrameBuilder("variable").add("name", vpOf(v).name$() + "_" + v.name$());
		if (v.isCyclic()) builder.add("cyclic");
		if (v.isNumeric()) builder.add("numeric");
		return builder.toFrame();
	}

	private ViewPoint vpOf(Variable v) {
		return v.core$().ownerAs(ViewPoint.class);
	}
}