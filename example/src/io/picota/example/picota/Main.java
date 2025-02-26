package io.picota.example.picota;

import io.picota.runtime.PicotaStarter;
import io.picota.runtime.RuntimeBox;

import java.io.IOException;

public class Main {
	public static void main(String[] args) throws IOException {
		RuntimeBox runtime = PicotaStarter.start(args, GraphLoader.load(args));
//		new InfecarDataPreparer(runtime.datahub().datalake(), runtime.datahub().stageDirectory(), Main.class.getResourceAsStream("/infecar.jsonl")).run();
		Runtime.getRuntime().addShutdownHook(new Thread(runtime::stop));
	}
}