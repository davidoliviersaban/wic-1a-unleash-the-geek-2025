import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class StupidAITest {

	private StupidAI ai;
	private GameState gameState;

	@BeforeEach
	public void setUp() {
		ai = new StupidAI();
		Map<Integer, City> cities = new HashMap<>();
		cities.put(0, new City(0, 0, 0, 0, List.of()));
		cities.put(1, new City(1, 2, 0, 0, List.of()));
		cities.put(2, new City(2, 0, 2, 0, List.of()));

		gameState = NAMOAStarTest.createGameStateWithCitiesAndTerrain(cities, Map.of(), TerrainType.PLAIN);
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