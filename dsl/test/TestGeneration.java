import io.picota.language.compiler.PicotacRunner;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;

public class TestGeneration {

	@Test
	@Ignore
	public void should_build_example() {
		PicotacRunner.main(new String[]{new File("./test-res/example.txt").getAbsolutePath()});
	}
}
