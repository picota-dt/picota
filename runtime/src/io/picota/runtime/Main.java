package io.picota.runtime;

public class Main {
	public static void main(String[] args) {
		RuntimeBox box = new RuntimeBox(args);
		box.start();
		Runtime.getRuntime().addShutdownHook(new Thread(box::stop));
	}
}