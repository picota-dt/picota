package io.picota.example.picota;

import io.picota.runtime.PicotaStarter;
import io.picota.runtime.RuntimeBox;

import java.io.IOException;

public class Main {
	public static void main(String[] args) throws IOException {
		RuntimeBox runtime = PicotaStarter.start(args, GraphLoader.load(args));
		Runtime.getRuntime().addShutdownHook(new Thread(runtime::stop));
	}
}