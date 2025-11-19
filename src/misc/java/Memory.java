import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.List;

public class Memory {

	// Handles memory stuff, see https://www.codingame.com/forum/t/java-jvm-memory-issues/1494/25
	public static void initMemory() {

		if (Player.isDebugOn) {
			String debugString = getJVMParams();
			Print.debug("JVM params: " + debugString);
			Print.debug("Starting memory before alloc:");
			debugCurrentMemory();
		}

		GameState[] alloc = new GameState[1000000];
		int i = 0;
		long maxMemory = Runtime.getRuntime().maxMemory();

		while (Runtime.getRuntime().totalMemory() < maxMemory * 0.9 && Time.isTimeLeft(true) && i < alloc.length) {
			alloc[i] = new GameState(i);
			i++;
		}

		alloc = null;
		System.gc();

		if (Player.isDebugOn) {
			Print.debug("New memory after alloc:");
			debugCurrentMemory();
		}

	}

	private static String getJVMParams() {
		// Display memory options
		RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
		List<String> arguments = runtimeMxBean.getInputArguments();
		String debugString = "";
		for (String string : arguments) {
			debugString += string + " ";
		}
		return debugString;
	}

	private static void debugCurrentMemory() {
		Print.debug("totalMemory: " + Runtime.getRuntime().totalMemory());
		Print.debug("maxMemory:   " + Runtime.getRuntime().maxMemory());
		Print.debug("freeMemory:  " + Runtime.getRuntime().freeMemory());
	}

}