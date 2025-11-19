import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashMap;
import java.util.Map;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class StupidAITest {

	private StupidAI ai;
	private GameState gameState;

	@BeforeEach
	public void setUp() {
		ai = new StupidAI();
	}

	@Test
	public void testComputeIntact() {
		List<Action> actions = ai.computeIntact(gameState);
		assertTrue(actions.size() > 0);
	}

	@Test
	public void testCompute() {
		List<Action> actions = ai.compute(gameState);
		assertTrue(actions.size() > 0);
	}

}