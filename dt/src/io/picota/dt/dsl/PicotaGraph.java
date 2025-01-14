package io.picota.dt.dsl;

import io.intino.magritte.framework.Graph;

public class PicotaGraph extends io.picota.dt.dsl.AbstractGraph {

	public PicotaGraph(Graph graph) {
		super(graph);
	}

	public PicotaGraph(io.intino.magritte.framework.Graph graph, PicotaGraph wrapper) {
	    super(graph, wrapper);
	}


	public static PicotaGraph load(io.intino.magritte.io.model.Stash... startingModel) {
		return new Graph().loadLanguage("Picota", _language()).loadStashes(startingModel).as(PicotaGraph.class);
	}

	public static PicotaGraph load(io.intino.magritte.framework.Store store, io.intino.magritte.io.model.Stash... startingModel) {
		return new Graph(store).loadLanguage("Picota", _language()).loadStashes(startingModel).as(PicotaGraph.class);
	}

	public static PicotaGraph load(String... startingModel) {
		return new Graph().loadLanguage("Picota", _language()).loadStashes(startingModel).as(PicotaGraph.class);
	}

	public static PicotaGraph load(io.intino.magritte.framework.Store store, String... startingModel) {
		return new Graph(store).loadLanguage("Picota", _language()).loadStashes(startingModel).as(PicotaGraph.class);
	}
}