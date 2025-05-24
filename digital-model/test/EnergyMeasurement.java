public class EnergyMeasurement {
	public Header header;
	public Voltage voltage;
	public Current current;
	public Power power;
	public ActiveEnergy active_energy;
	public ReactiveEnergy reactive_energy;

	public static class Header {
		public String ts;
		public String MAC;
		public String alias;
	}

	public static class Voltage {
		public double[] line;
		public double[] phase;
		public double[] freq;
		public double[] THD;
	}

	public static class Current {
		public double[] line;
		public double[] THD;
	}

	public static class Power {
		public double[] active;
		public double[] reactive;
		public double[] apparent;
		public double[] factor;
	}

	public static class ActiveEnergy {
		public long[] imported;
		public long[] exported;
	}

	public static class ReactiveEnergy {
		public long[] imported;
		public long[] exported;
	}
}
