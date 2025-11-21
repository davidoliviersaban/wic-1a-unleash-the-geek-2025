import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class StupidAITest {

	private SimpleAI ai;
	private GameState gameState;

	@BeforeEach
	public void setUp() {
		MatchConstants.width = 30;
		MatchConstants.height = 20;
		MatchConstants.initCoords(30, 20);

		ai = new SimpleAI();
		Map<Integer, City> cities = new HashMap<>();
		cities.put(0, new City(0, 0, 0, 0, List.of(1, 2)));
		cities.put(1, new City(1, 2, 0, 0, List.of()));
		cities.put(2, new City(2, 0, 2, 0, List.of()));
		cities.put(3, new City(3, 20, 12, 0, List.of()));
		cities.put(4, new City(4, 2, 2, 0, List.of()));

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

	@Test
	public void testFindSortedCheapestPathsOrdersByBuildCost() {
		City origin = gameState.map().citiesById()[0];
		City target1 = gameState.map().citiesById()[1];
		City target2 = gameState.map().citiesById()[2];

		NAMOAPath cheap = path(origin, target1,
				List.of(coord(0, 0), coord(1, 0), coord(2, 0)), 2, 2);
		NAMOAPath expensive = path(origin, target2,
				List.of(coord(0, 0), coord(0, 1), coord(0, 2)), 3, 6);

		Map<Integer, List<NAMOAPath>> perTarget = new LinkedHashMap<>();
		perTarget.put(target1.id(), List.of(expensive));
		perTarget.put(target2.id(), List.of(cheap));

		Map<Integer, NAMOAPathsForCity> cache = new HashMap<>();
		cache.put(origin.id(), new NAMOAPathsForCity(origin, perTarget));

		List<NAMOAPath> sortedPaths = ai.findSortedCheapestPaths(gameState, cache);

		assertEquals(List.of(cheap, expensive), sortedPaths);
	}

	@Test
	public void testFilterPathsByBuildCostRespectsThreshold() {
		City origin = gameState.map().citiesById()[0];
		City target = gameState.map().citiesById()[1];
		List<NAMOAPath> paths = List.of(
				path(origin, target, List.of(coord(0, 0), coord(1, 0)), 1, 1),
				path(origin, target, List.of(coord(0, 0), coord(1, 1)), 2, 4),
				path(origin, target, List.of(coord(0, 0), coord(1, 2)), 3, 2));

		List<NAMOAPath> filtered = ai.filterPathsByBuildCost(paths, 2);

		assertEquals(2, filtered.size());
		assertTrue(filtered.stream().allMatch(p -> p.buildCost() <= 2));
	}

	@Test
	public void testGetTopPathsClampsToRequestedCount() {
		City origin = gameState.map().citiesById()[0];
		City target = gameState.map().citiesById()[1];
		List<NAMOAPath> sortedPaths = new ArrayList<>();
		sortedPaths.add(path(origin, target, List.of(coord(0, 0), coord(1, 0)), 1, 1));
		sortedPaths.add(path(origin, target, List.of(coord(0, 0), coord(1, 1)), 2, 2));
		sortedPaths.add(path(origin, target, List.of(coord(0, 0), coord(1, 2)), 3, 3));
		sortedPaths.add(path(origin, target, List.of(coord(0, 0), coord(1, 3)), 4, 4));

		List<NAMOAPath> topThree = ai.getTopPaths(sortedPaths, 3);

		assertEquals(sortedPaths.subList(0, 3), topThree);
	}

	@Test
	public void testBuildRailsAlongPathRespectsCapacityAndSkipsCities() {
		City origin = gameState.map().citiesById()[0];
		City target = gameState.map().citiesById()[1];
		List<Coord> coords = List.of(
				coord(0, 0), // start city should be skipped
				coord(1, 0), coord(1, 1), coord(1, 2), coord(1, 3));
		NAMOAPath candidate = path(origin, target, coords, 5, 5);

		List<Action> actions = ai.buildRailsAlongPath(gameState, List.of(candidate));

		assertEquals(MatchConstants.MAX_ACTIONS_PER_TURN, actions.size());
		Set<Coord> builtCoords = actions.stream().map(Action::coord1).collect(Collectors.toSet());
		assertFalse(builtCoords.contains(coord(0, 0)), "Cities should not get rails built on them");
		assertTrue(builtCoords.stream().allMatch(coords::contains));
	}

	@Test
	public void testBuildRailsAcrossTwoRegionsDistributesActions() {
		GameState multiRegionState = createMultiRegionGameState();
		City origin = multiRegionState.map().citiesById()[0];
		City target = multiRegionState.map().citiesById()[1];
		List<Coord> coords = List.of(
				coord(origin.x(), origin.y()),
				coord(1, 0), // region 0
				coord(1, 1), // region 0
				coord(1, 2), // region 0
				coord(2, 0), // first region 1 coord appears late in the path
				coord(target.x(), target.y()));
		NAMOAPath candidate = path(origin, target, coords, 5, 5);
		boolean previous = SimpleAI.BUILD_ONLY_IN_ONE_REGION_PER_TURN;
		try {
			SimpleAI.BUILD_ONLY_IN_ONE_REGION_PER_TURN = false;
			List<Action> withoutConstraint = ai.buildRailsAlongPath(multiRegionState, List.of(candidate));
			assertEquals(MatchConstants.MAX_ACTIONS_PER_TURN, withoutConstraint.size());
			Map<Integer, Long> countsWithout = groupByRegion(multiRegionState, withoutConstraint);
			assertEquals(3L, countsWithout.getOrDefault(0, 0L));
			assertEquals(0L, countsWithout.getOrDefault(1, 0L));

			SimpleAI.BUILD_ONLY_IN_ONE_REGION_PER_TURN = true;
			List<Action> withConstraint = ai.buildRailsAlongPath(multiRegionState, List.of(candidate));
			assertEquals(MatchConstants.MAX_ACTIONS_PER_TURN, withConstraint.size());
			Map<Integer, Long> countsWith = groupByRegion(multiRegionState, withConstraint);
			assertEquals(2L, countsWith.getOrDefault(0, 0L));
			assertEquals(1L, countsWith.getOrDefault(1, 0L));
		} finally {
			SimpleAI.BUILD_ONLY_IN_ONE_REGION_PER_TURN = previous;
		}
	}

	private NAMOAPath path(City from, City to, List<Coord> coords, int distance, int buildCost) {
		return new NAMOAPath(from, to, coords, new PathCost(distance, buildCost));
	}

	private Coord coord(int x, int y) {
		return MatchConstants.coord(x, y);
	}

	private GameState createMultiRegionGameState() {
		int width = 4;
		int height = 3;
		int regionWidth = 2;
		int regionCount = (int) Math.ceil((double) width / regionWidth);

		TerrainType[][] terrain = new TerrainType[width][height];
		int[][] regionIds = new int[width][height];
		int[][] cityIds = new int[width][height];
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				terrain[x][y] = TerrainType.PLAIN;
				regionIds[x][y] = Math.min(x / regionWidth, regionCount - 1);
				cityIds[x][y] = -1;
			}
		}

		City origin = new City(0, 0, 0, regionIds[0][0], List.of(1));
		City target = new City(1, 3, 2, regionIds[3][2], List.of());
		int maxCityId = Math.max(origin.id(), target.id());
		City[] citiesById = new City[maxCityId + 1];
		citiesById[origin.id()] = origin;
		citiesById[target.id()] = target;
		cityIds[origin.x()][origin.y()] = origin.id();
		cityIds[target.x()][target.y()] = target.id();

		Map<Integer, List<Tile>> cellsByRegion = new HashMap<>();
		Map<Integer, Boolean> regionHasCity = new HashMap<>();
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				City city = cityIds[x][y] >= 0 ? citiesById[cityIds[x][y]] : null;
				Tile tile = new Tile(x, y, regionIds[x][y], terrain[x][y], city);
				cellsByRegion.computeIfAbsent(regionIds[x][y], k -> new ArrayList<>()).add(tile);
				if (city != null) {
					regionHasCity.put(regionIds[x][y], true);
				}
			}
		}

		Region[] regions = new Region[regionCount];
		for (int id = 0; id < regionCount; id++) {
			List<Tile> cells = cellsByRegion.getOrDefault(id, new ArrayList<>());
			regions[id] = new Region(id, 0, cells, new HashSet<>(), regionHasCity.getOrDefault(id, false));
		}

		MatchConstants.cityCount = citiesById.length;
		MatchConstants.regionsCount = regions.length;
		MatchConstants.connectionLookup = new Connection[citiesById.length][citiesById.length];
		for (int i = 0; i < citiesById.length; i++) {
			City cityI = citiesById[i];
			if (cityI == null) {
				continue;
			}
			for (int j = 0; j < citiesById.length; j++) {
				City cityJ = citiesById[j];
				if (cityJ == null) {
					continue;
				}
				MatchConstants.connectionLookup[i][j] = new Connection(cityI.id(), cityJ.id());
			}
		}

		MapDefinition map = new MapDefinition(width, height, terrain, regionIds, cityIds, citiesById, regions);
		return new GameState(1, map, new HashMap<Coord, Rail>(), 0, 0, new HashSet<>());
	}

	private Map<Integer, Long> groupByRegion(GameState gs, List<Action> actions) {
		return actions.stream().collect(Collectors.groupingBy(action -> gs.regionIdAt(action.coord1()),
				Collectors.counting()));
	}

}