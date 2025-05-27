public class MeteoMeasurement {
	public Header header;
	public Extras extras;

	public static class Header {
		public String ts;
		public String MAC;
		public String alias;
	}

	public static class Extras {
		public int temp_cell;
		public int temp_envi;
		public int radiation;
	}
}
