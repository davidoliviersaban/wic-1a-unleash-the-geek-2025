import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RailPathfinderTest {

	@BeforeEach
	public void setUp() {
		MatchConstants.width = 10;
		MatchConstants.height = 10;
		MatchConstants.initCoords(10, 10);
	}

	@Test
	public void testFindShortestRailPath_DirectConnection() {
		// Create a simple grid with two cities connected by rails
		// Cities at (0,0) and (3,0), with rails at (1,0) and (2,0)
		GameState gs = createGameStateWithRails(
				createTwoCity(0, 0, 3, 0),
				createRailsBetween(0, 0, 3, 0));

		City city1 = gs.map().citiesById().get(0);
		City city2 = gs.map().citiesById().get(1);

		// Warmup run
		RailPathfinder.findShortestRailPath(gs, city1, city2);

		long startTime = System.nanoTime();
		List<Coord> path = RailPathfinder.findShortestRailPath(gs, city1, city2);
		long duration = (System.nanoTime() - startTime) / 1_000_000;

		assertTrue(duration < 50, "Pathfinding took " + duration + "ms, expected < 50ms");

		assertNotNull(path);
		assertEquals(4, path.size()); // (0,0), (1,0), (2,0), (3,0)
		assertEquals(MatchConstants.coord(0, 0), path.get(0));
		assertEquals(MatchConstants.coord(3, 0), path.get(3));
	}

	@Test
	public void testFindShortestRailPath_NoConnection() {
		// Two cities with no rails between them
		GameState gs = createGameStateWithRails(
				createTwoCity(0, 0, 5, 5),
				Map.of());

		City city1 = gs.map().citiesById().get(0);
		City city2 = gs.map().citiesById().get(1);

		List<Coord> path = RailPathfinder.findShortestRailPath(gs, city1, city2);

		assertNull(path);
	}

	@Test
	public void testFindShortestRailPath_LShapedPath() {
		// Create an L-shaped path: (0,0) -> (2,0) -> (2,2)
		Map<Coord, Rail> rails = new HashMap<>();
		rails.put(MatchConstants.coord(1, 0), new Rail(1, 0, RailOwner.ME));
		rails.put(MatchConstants.coord(2, 0), new Rail(2, 0, RailOwner.ME));
		rails.put(MatchConstants.coord(2, 1), new Rail(2, 1, RailOwner.ME));

		GameState gs = createGameStateWithRails(
				createTwoCity(0, 0, 2, 2),
				rails);

		City city1 = gs.map().citiesById().get(0);
		City city2 = gs.map().citiesById().get(1);

		// Warmup run
		RailPathfinder.findShortestRailPath(gs, city1, city2);

		long startTime = System.nanoTime();
		List<Coord> path = RailPathfinder.findShortestRailPath(gs, city1, city2);
		long duration = (System.nanoTime() - startTime) / 1_000_000;

		assertTrue(duration < 50, "Pathfinding took " + duration + "ms, expected < 50ms");

		assertNotNull(path);
		assertEquals(5, path.size());
		assertEquals(MatchConstants.coord(0, 0), path.get(0));
		assertEquals(MatchConstants.coord(2, 2), path.get(4));
	}

	@Test
	public void testFindShortestRailPath_SameCity() {
		GameState gs = createGameStateWithRails(
				createTwoCity(0, 0, 5, 5),
				Map.of());

		City city1 = gs.map().citiesById().get(0);

		List<Coord> path = RailPathfinder.findShortestRailPath(gs, city1, city1);

		assertNotNull(path);
		assertEquals(1, path.size());
		assertEquals(MatchConstants.coord(0, 0), path.get(0));
	}

	@Test
	public void testFindConnectedCityPairs_NoCities() {
		GameState gs = GameState.createInitial(10, 10);

		List<CityConnection> connections = RailPathfinder.findConnectedCityPairs(gs);

		assertNotNull(connections);
		assertTrue(connections.isEmpty());
	}

	@Test
	public void testFindConnectedCityPairs_TwoCitiesConnected() {
		GameState gs = createGameStateWithRails(
				createTwoCity(0, 0, 2, 0),
				createRailsBetween(0, 0, 2, 0));

		List<CityConnection> connections = RailPathfinder.findConnectedCityPairs(gs);

		assertNotNull(connections);
		assertEquals(1, connections.size());

		CityConnection conn = connections.get(0);
		assertEquals(0, conn.from().id());
		assertEquals(1, conn.to().id());
		assertEquals(3, conn.distance());
		assertNotNull(conn.path());
	}

	@Test
	public void testFindConnectedCityPairs_ThreeCitiesPartiallyConnected() {
		// City 0 at (0,0), City 1 at (2,0), City 2 at (4,0)
		// Rails connect 0-1 but not 1-2
		Map<Integer, City> cities = new HashMap<>();
		cities.put(0, new City(0, 0, 0, 0, List.of()));
		cities.put(1, new City(1, 2, 0, 0, List.of()));
		cities.put(2, new City(2, 4, 0, 0, List.of()));

		Map<Coord, Rail> rails = new HashMap<>();
		rails.put(MatchConstants.coord(1, 0), new Rail(1, 0, RailOwner.ME));

		GameState gs = createGameStateWithCitiesAndRails(cities, rails);

		List<CityConnection> connections = RailPathfinder.findConnectedCityPairs(gs);

		assertNotNull(connections);
		assertEquals(1, connections.size());
		assertEquals(0, connections.get(0).from().id());
		assertEquals(1, connections.get(0).to().id());
	}

	@Test
	public void testFindConnectedCityPairs_MultipleConnections() {
		// Three cities in a line, all connected
		Map<Integer, City> cities = new HashMap<>();
		cities.put(0, new City(0, 0, 0, 0, List.of()));
		cities.put(1, new City(1, 2, 0, 0, List.of()));
		cities.put(2, new City(2, 4, 0, 0, List.of()));

		Map<Coord, Rail> rails = new HashMap<>();
		rails.put(MatchConstants.coord(1, 0), new Rail(1, 0, RailOwner.ME));
		rails.put(MatchConstants.coord(2, 0), new Rail(2, 0, RailOwner.ME));
		rails.put(MatchConstants.coord(3, 0), new Rail(3, 0, RailOwner.ME));

		GameState gs = createGameStateWithCitiesAndRails(cities, rails);

		// Warmup run
		RailPathfinder.findConnectedCityPairs(gs);

		long startTime = System.nanoTime();
		List<CityConnection> connections = RailPathfinder.findConnectedCityPairs(gs);
		long duration = (System.nanoTime() - startTime) / 1_000_000;

		assertTrue(duration < 50, "Pathfinding took " + duration + "ms, expected < 50ms");

		assertNotNull(connections);
		assertEquals(3, connections.size()); // 0-1, 0-2, 1-2
	}

	@Test
	public void testFindShortestRailPath_WithContestedRails() {
		// Test that contested rails still work as connections
		Map<Coord, Rail> rails = new HashMap<>();
		rails.put(MatchConstants.coord(1, 0), new Rail(1, 0, RailOwner.CONTESTED));

		GameState gs = createGameStateWithRails(
				createTwoCity(0, 0, 2, 0),
				rails);

		City city1 = gs.map().citiesById().get(0);
		City city2 = gs.map().citiesById().get(1);

		List<Coord> path = RailPathfinder.findShortestRailPath(gs, city1, city2);

		assertNotNull(path);
		assertEquals(3, path.size());
	}

	@Test
	public void testFindShortestRailPath_WithOpponentRails() {
		// Test that opponent rails work as connections
		Map<Coord, Rail> rails = new HashMap<>();
		rails.put(MatchConstants.coord(1, 0), new Rail(1, 0, RailOwner.OPPONENT));

		GameState gs = createGameStateWithRails(
				createTwoCity(0, 0, 2, 0),
				rails);

		City city1 = gs.map().citiesById().get(0);
		City city2 = gs.map().citiesById().get(1);

		List<Coord> path = RailPathfinder.findShortestRailPath(gs, city1, city2);

		assertNotNull(path);
		assertEquals(3, path.size());
	}

	// Helper methods

	private Map<Integer, City> createTwoCity(int x1, int y1, int x2, int y2) {
		Map<Integer, City> cities = new HashMap<>();
		cities.put(0, new City(0, x1, y1, 0, List.of()));
		cities.put(1, new City(1, x2, y2, 0, List.of()));
		return cities;
	}

	private Map<Coord, Rail> createRailsBetween(int x1, int y1, int x2, int y2) {
		Map<Coord, Rail> rails = new HashMap<>();
		if (y1 == y2) {
			// Horizontal line
			int start = Math.min(x1, x2);
			int end = Math.max(x1, x2);
			for (int x = start + 1; x < end; x++) {
				rails.put(MatchConstants.coord(x, y1), new Rail(x, y1, RailOwner.ME));
			}
		} else if (x1 == x2) {
			// Vertical line
			int start = Math.min(y1, y2);
			int end = Math.max(y1, y2);
			for (int y = start + 1; y < end; y++) {
				rails.put(MatchConstants.coord(x1, y), new Rail(x1, y, RailOwner.ME));
			}
		}
		return rails;
	}

	private GameState createGameStateWithRails(Map<Integer, City> cities, Map<Coord, Rail> rails) {
		return createGameStateWithCitiesAndRails(cities, rails);
	}

	private GameState createGameStateWithCitiesAndRails(Map<Integer, City> cities, Map<Coord, Rail> rails) {
		TerrainCell[][] terrain = new TerrainCell[10][10];
		for (int x = 0; x < 10; x++) {
			for (int y = 0; y < 10; y++) {
				terrain[x][y] = new TerrainCell(x, y, TerrainType.PLAIN, null);
			}
		}

		// Place cities on terrain
		for (City city : cities.values()) {
			terrain[city.x()][city.y()] = new TerrainCell(city.x(), city.y(), TerrainType.PLAIN, city);
		}

		MapDefinition map = new MapDefinition(10, 10, terrain, cities, Map.of(), 0);
		return new GameState(1, map, rails, new Region[0], 0, 0, null);
	}
}
