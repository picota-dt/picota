package io.picota.example.picota;

import io.picota.runtime.PicotaStarter;
import io.picota.runtime.RuntimeBox;

public class Main {
	public static void main(String[] args) {
		RuntimeBox runtime = PicotaStarter.start(args, GraphLoader.load(args));
		Runtime.getRuntime().addShutdownHook(new Thread(runtime::stop));
	}
}