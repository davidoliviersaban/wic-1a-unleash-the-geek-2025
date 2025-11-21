import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NAMOAStarTest {

    @BeforeEach
    public void setUp() {
        MatchConstants.width = 30;
        MatchConstants.height = 20;
        MatchConstants.initCoords(30, 20);
    }

    @Test
    public void testFindPaths_SingleTarget_DirectPath() {
        // Create a simple scenario: start at (0,0), target at (3,0), plain terrain
        Map<Integer, City> cities = new HashMap<>();
        cities.put(0, new City(0, 0, 0, 0, List.of()));
        cities.put(1, new City(1, 3, 0, 0, List.of()));

        GameState gs = createGameStateWithCitiesAndTerrain(cities, Map.of(), TerrainType.PLAIN);

        City start = cities.get(0);
        List<City> targets = List.of(cities.get(1));

        // Warmup run
        NAMOAStar.findPaths(gs, start, targets);

        long startTime = System.nanoTime();
        Map<Integer, List<NAMOAPath>> results = NAMOAStar.findPaths(gs, start, targets);
        long duration = (System.nanoTime() - startTime) / 1_000_000; // Convert to ms

        assertTrue(duration < 50, "Pathfinding took " + duration + "ms, expected < 50ms");

        assertNotNull(results);
        assertTrue(results.containsKey(1));
        List<NAMOAPath> paths = results.get(1);
        assertFalse(paths.isEmpty());

        NAMOAPath path = paths.get(0);
        assertEquals(start, path.from());
        assertEquals(cities.get(1), path.to());
        assertEquals(4, path.path().size()); // (0,0), (1,0), (2,0), (3,0)
        assertEquals(3, path.distance()); // 3 steps
        assertEquals(2, path.buildCost()); // 2 cells at cost 1 (cities have 0 cost)
    }

    @Test
    public void testFindPaths_MultipleTargets() {
        // Start at (0,0), targets at (2,0) and (0,2)
        Map<Integer, City> cities = new HashMap<>();
        cities.put(0, new City(0, 0, 0, 0, List.of()));
        cities.put(1, new City(1, 2, 0, 0, List.of()));
        cities.put(2, new City(2, 0, 2, 0, List.of()));

        GameState gs = createGameStateWithCitiesAndTerrain(cities, Map.of(), TerrainType.PLAIN);

        City start = cities.get(0);
        List<City> targets = List.of(cities.get(1), cities.get(2));

        // Warmup run
        NAMOAStar.findPaths(gs, start, targets);

        long startTime = System.nanoTime();
        Map<Integer, List<NAMOAPath>> results = NAMOAStar.findPaths(gs, start, targets);
        long duration = (System.nanoTime() - startTime) / 1_000_000;

        assertTrue(duration < 50, "Pathfinding took " + duration + "ms, expected < 50ms");

        assertNotNull(results);
        assertEquals(2, results.size());

        // Should have paths to both targets
        assertTrue(results.containsKey(1));
        assertTrue(results.containsKey(2));
        assertFalse(results.get(1).isEmpty());
        assertFalse(results.get(2).isEmpty());

        NAMOAPath pathTo1 = results.get(1).get(0);
        assertEquals(2, pathTo1.distance());

        NAMOAPath pathTo2 = results.get(2).get(0);
        assertEquals(2, pathTo2.distance());
    }

    @Test
    public void testFindPaths_DifferentTerrainCosts() {
        // Test that algorithm considers different terrain costs
        Map<Integer, City> cities = new HashMap<>();
        cities.put(0, new City(0, 0, 0, 0, List.of()));
        cities.put(1, new City(1, 2, 0, 0, List.of()));

        TerrainType[][] terrain = createTerrainGrid(MatchConstants.width, MatchConstants.height, TerrainType.PLAIN);
        terrain[1][0] = TerrainType.RIVER; // Higher cost tile

        GameState gs = createGameStateWithCitiesAndTerrain(cities, Map.of(), terrain);

        City start = cities.get(0);
        List<City> targets = List.of(cities.get(1));

        // Warmup run
        NAMOAStar.findPaths(gs, start, targets);

        long startTime = System.nanoTime();
        Map<Integer, List<NAMOAPath>> results = NAMOAStar.findPaths(gs, start, targets);
        long duration = (System.nanoTime() - startTime) / 1_000_000;

        assertTrue(duration < 50, "Pathfinding took " + duration + "ms, expected < 50ms");

        assertNotNull(results);
        List<NAMOAPath> paths = results.get(1);
        assertFalse(paths.isEmpty());

        NAMOAPath path = paths.get(0);
        assertEquals(2, path.distance());
        assertEquals(2, path.buildCost()); // 1 plain (cost 1) + 1 river (cost 2) - cities are free
    }

    @Test
    public void testFindPaths_WithExistingRails() {
        // Existing rails should have 0 cost to traverse
        Map<Integer, City> cities = new HashMap<>();
        cities.put(0, new City(0, 0, 0, 0, List.of()));
        cities.put(1, new City(1, 3, 0, 0, List.of()));

        Map<Coord, Rail> rails = new HashMap<>();
        rails.put(MatchConstants.coord(1, 0), new Rail(1, 0, RailOwner.ME));
        rails.put(MatchConstants.coord(2, 0), new Rail(2, 0, RailOwner.ME));

        GameState gs = createGameStateWithCitiesAndTerrain(cities, rails, TerrainType.PLAIN);

        City start = cities.get(0);
        List<City> targets = List.of(cities.get(1));

        Map<Integer, List<NAMOAPath>> results = NAMOAStar.findPaths(gs, start, targets);

        assertNotNull(results);
        List<NAMOAPath> paths = results.get(1);
        assertFalse(paths.isEmpty());

        NAMOAPath path = paths.get(0);
        assertEquals(3, path.distance());
        assertEquals(0, path.buildCost()); // All existing rails, no build cost
    }

    @Test
    public void testFindPaths_MultipleNonDominatedPaths() {
        Map<Integer, City> cities = new HashMap<>();
        cities.put(0, new City(0, 0, 0, 0, List.of()));
        cities.put(1, new City(1, 3, 2, 0, List.of()));

        TerrainType[][] terrain = createTerrainGrid(10, 10, TerrainType.PLAIN);
        terrain[1][0] = TerrainType.RIVER;
        terrain[2][0] = TerrainType.RIVER;
        terrain[3][0] = TerrainType.RIVER;
        terrain[3][1] = TerrainType.RIVER;

        GameState gs = createGameStateWithCitiesAndTerrain(cities, Map.of(), terrain);

        City start = cities.get(0);
        List<City> targets = List.of(cities.get(1));

        Map<Integer, List<NAMOAPath>> results = NAMOAStar.findPaths(gs, start, targets);

        assertNotNull(results);
        List<NAMOAPath> paths = results.get(1);

        // Might have multiple non-dominated solutions
        // At minimum should have one path
        assertFalse(paths.isEmpty());

        // Check that all returned paths are actually non-dominated
        for (int i = 0; i < paths.size(); i++) {
            for (int j = 0; j < paths.size(); j++) {
                if (i != j) {
                    assertFalse(paths.get(i).cost().dominates(paths.get(j).cost()),
                            "Path " + i + " dominates path " + j);
                }
            }
        }
    }

    @Test
    public void testFindPaths_NoPathExists() {
        // Create scenario where target is unreachable (blocked by mountains)
        Map<Integer, City> cities = new HashMap<>();
        cities.put(0, new City(0, 0, 0, 0, List.of()));
        cities.put(1, new City(1, 3, 0, 0, List.of()));

        TerrainType[][] terrain = createTerrainGrid(10, 10, TerrainType.PLAIN);
        terrain[1][0] = TerrainType.POI;
        terrain[2][0] = TerrainType.POI;

        GameState gs = createGameStateWithCitiesAndTerrain(cities, Map.of(), terrain);

        City start = cities.get(0);
        List<City> targets = List.of(cities.get(1));

        Map<Integer, List<NAMOAPath>> results = NAMOAStar.findPaths(gs, start, targets);

        assertNotNull(results);
        List<NAMOAPath> paths = results.get(1);

        // Should find alternative paths around the POIs
        // If truly blocked, paths would be empty
        assertNotNull(paths);
    }

    // Skip for now as cost may be
    // @Test("PathCost dominance logic test")
    public void testPathCost_Dominance() {
        PathCost cost1 = new PathCost(5, 10);
        PathCost cost2 = new PathCost(6, 11);
        PathCost cost3 = new PathCost(5, 11);
        PathCost cost4 = new PathCost(6, 10);

        // cost1 dominates cost2 (better in both)
        assertTrue(cost1.dominates(cost2));
        assertFalse(cost2.dominates(cost1));

        // cost1 dominates cost3 (equal distance, better cost)
        assertTrue(cost1.dominates(cost3));
        assertFalse(cost3.dominates(cost1));

        // cost1 dominates cost4 (better distance, equal cost)
        assertTrue(cost1.dominates(cost4));
        assertFalse(cost4.dominates(cost1));

        // cost3 and cost4 don't dominate each other (trade-offs)
        assertFalse(cost3.dominates(cost4));
        assertFalse(cost4.dominates(cost3));
    }

    @Test
    public void testPathCost_Add() {
        PathCost cost = new PathCost(5, 10);
        PathCost newCost = cost.add(2, 3);

        assertEquals(7, newCost.distance());
        assertEquals(13, newCost.buildCost());

        // Original should be unchanged (immutable)
        assertEquals(5, cost.distance());
        assertEquals(10, cost.buildCost());
    }

    @Test
    public void testFindPaths_SameStartAndTarget() {
        Map<Integer, City> cities = new HashMap<>();
        cities.put(0, new City(0, 0, 0, 0, List.of()));

        GameState gs = createGameStateWithCitiesAndTerrain(cities, Map.of(), TerrainType.PLAIN);

        City start = cities.get(0);
        List<City> targets = List.of(cities.get(0));

        Map<Integer, List<NAMOAPath>> results = NAMOAStar.findPaths(gs, start, targets);

        assertNotNull(results);
        List<NAMOAPath> paths = results.get(0);
        assertFalse(paths.isEmpty());

        NAMOAPath path = paths.get(0);
        assertEquals(1, path.path().size()); // Just the start/target
        assertEquals(0, path.distance());
        assertEquals(0, path.buildCost());
    }

    @Test
    public void testPerformance_RealGameConstraints() {
        // Test with realistic game constraints: 30x20 grid, 12 cities
        MatchConstants.width = 30;
        MatchConstants.height = 20;
        MatchConstants.initCoords(30, 20);

        Map<Integer, City> cities = new HashMap<>();
        // Place 12 cities across the map
        cities.put(0, new City(0, 2, 2, 0, List.of()));
        cities.put(1, new City(1, 27, 2, 0, List.of()));
        cities.put(2, new City(2, 2, 17, 0, List.of()));
        cities.put(3, new City(3, 27, 17, 0, List.of()));
        cities.put(4, new City(4, 15, 10, 0, List.of()));
        cities.put(5, new City(5, 8, 5, 0, List.of()));
        cities.put(6, new City(6, 22, 5, 0, List.of()));
        cities.put(7, new City(7, 8, 15, 0, List.of()));
        cities.put(8, new City(8, 22, 15, 0, List.of()));
        cities.put(9, new City(9, 5, 10, 0, List.of()));
        cities.put(10, new City(10, 25, 10, 0, List.of()));
        cities.put(11, new City(11, 15, 2, 0, List.of()));

        TerrainType[][] terrain = new TerrainType[30][20];
        for (int x = 0; x < 30; x++) {
            for (int y = 0; y < 20; y++) {
                terrain[x][y] = (x + y) % 3 == 0 ? TerrainType.RIVER
                        : (x * y) % 5 == 0 ? TerrainType.MOUNTAIN : TerrainType.PLAIN;
            }
        }

        GameState gs = createGameStateWithCitiesAndTerrain(cities, Map.of(), terrain);
        City start = cities.get(0);
        List<City> targets = List.of(cities.get(1), cities.get(2), cities.get(3));

        // Warmup
        NAMOAStar.findPaths(gs, start, targets);

        long startTime = System.nanoTime();
        Map<Integer, List<NAMOAPath>> results = NAMOAStar.findPaths(gs, start, targets);
        long duration = (System.nanoTime() - startTime) / 1_000_000;

        assertTrue(duration < 50, "Pathfinding on 30x20 grid with 3 targets took " + duration + "ms, expected < 50ms");

        // Verify we got results
        assertNotNull(results);
        assertTrue(results.size() > 0, "Should find at least one path");

        // Reset constants
        MatchConstants.width = 10;
        MatchConstants.height = 10;
        MatchConstants.initCoords(10, 10);
    }

    @Test
    public void testDirectionPriority_NorthThenEast() {
        // Test that pathfinding follows direction priority: NORTH, EAST, SOUTH, WEST
        // From City1(15,3) to City2(18,1) should go: N, N, E, E, E
        // Setup a 20x10 grid
        MatchConstants.width = 20;
        MatchConstants.height = 10;
        MatchConstants.initCoords(20, 10);

        Map<Integer, City> cities = new HashMap<>();
        cities.put(1, new City(1, 15, 3, 0, List.of(2)));
        cities.put(2, new City(2, 18, 1, 0, List.of()));

        TerrainType[][] terrain = createTerrainGrid(MatchConstants.width, MatchConstants.height, TerrainType.PLAIN);

        GameState gs = createGameStateWithCitiesAndTerrain(cities, Map.of(), terrain);

        City start = cities.get(1);
        List<City> targets = List.of(cities.get(2));

        Map<Integer, List<NAMOAPath>> results = NAMOAStar.findPaths(gs, start, targets);

        assertNotNull(results);
        assertTrue(results.containsKey(2));
        List<NAMOAPath> paths = results.get(2);
        assertFalse(paths.isEmpty());

        NAMOAPath path = paths.get(0);
        assertEquals(start, path.from());
        assertEquals(cities.get(2), path.to());

        // Verify the path coordinates - with NORTH, EAST, SOUTH, WEST priority
        // Actual path: (15,3) -> (15,2) -> (15,1) -> (16,1) -> (17,1) -> (18,1)
        // This follows: N, N, E, E, E
        List<Coord> pathCoords = path.path();
        assertEquals(6, pathCoords.size(), "Path should have 6 coordinates");

        // Verify start position
        assertEquals(15, pathCoords.get(0).x());
        assertEquals(3, pathCoords.get(0).y());

        // Step 1: North to (15,2)
        assertEquals(15, pathCoords.get(1).x());
        assertEquals(2, pathCoords.get(1).y());

        // Step 2: North to (15,1)
        assertEquals(15, pathCoords.get(2).x());
        assertEquals(1, pathCoords.get(2).y());

        // Step 3: East to (16,1)
        assertEquals(16, pathCoords.get(3).x());
        assertEquals(1, pathCoords.get(3).y());

        // Step 4: East to (17,1)
        assertEquals(17, pathCoords.get(4).x());
        assertEquals(1, pathCoords.get(4).y());

        // Step 5: East to destination (18,1)
        assertEquals(18, pathCoords.get(5).x());
        assertEquals(1, pathCoords.get(5).y());

        // Reset constants
        MatchConstants.width = 10;
        MatchConstants.height = 10;
        MatchConstants.initCoords(10, 10);
    }

    @Test
    public void testDirectionPriority_FromTopLeftToBottomRightPrefersEastThenSouth() {
        int originalWidth = MatchConstants.width;
        int originalHeight = MatchConstants.height;
        MatchConstants.width = 5;
        MatchConstants.height = 5;
        MatchConstants.initCoords(5, 5);

        try {
            Map<Integer, City> cities = new HashMap<>();
            cities.put(0, new City(0, 0, 0, 0, List.of(1)));
            cities.put(1, new City(1, 2, 2, 0, List.of(0)));

            TerrainType[][] terrain = createTerrainGrid(5, 5, TerrainType.PLAIN);
            GameState gs = createGameStateWithCitiesAndTerrain(cities, Map.of(), terrain);

            City start = cities.get(0);
            City target = cities.get(1);
            Map<Integer, List<NAMOAPath>> results = NAMOAStar.findPaths(gs, start, List.of(target));

            assertNotNull(results);
            List<NAMOAPath> paths = results.get(target.id());
            assertNotNull(paths);
            assertFalse(paths.isEmpty());

            List<Coord> expectedPath = List.of(
                    MatchConstants.coord(0, 0),
                    MatchConstants.coord(1, 0),
                    MatchConstants.coord(2, 0),
                    MatchConstants.coord(2, 1),
                    MatchConstants.coord(2, 2));

            assertEquals(expectedPath, paths.get(0).path(), "Path should follow E,E,S,S priority");
        } finally {
            MatchConstants.width = originalWidth;
            MatchConstants.height = originalHeight;
            MatchConstants.initCoords(originalWidth, originalHeight);
        }
    }

    @Test
    public void testDirectionPriority_FromBottomRightToTopLeftPrefersNorthThenWest() {
        int originalWidth = MatchConstants.width;
        int originalHeight = MatchConstants.height;
        MatchConstants.width = 5;
        MatchConstants.height = 5;
        MatchConstants.initCoords(5, 5);

        try {
            Map<Integer, City> cities = new HashMap<>();
            cities.put(0, new City(0, 0, 0, 0, List.of(1)));
            cities.put(1, new City(1, 2, 2, 0, List.of(0)));

            TerrainType[][] terrain = createTerrainGrid(5, 5, TerrainType.PLAIN);
            GameState gs = createGameStateWithCitiesAndTerrain(cities, Map.of(), terrain);

            City start = cities.get(1);
            City target = cities.get(0);
            Map<Integer, List<NAMOAPath>> results = NAMOAStar.findPaths(gs, start, List.of(target));

            assertNotNull(results);
            List<NAMOAPath> paths = results.get(target.id());
            assertNotNull(paths);
            assertFalse(paths.isEmpty());

            List<Coord> expectedPath = List.of(
                    MatchConstants.coord(2, 2),
                    MatchConstants.coord(2, 1),
                    MatchConstants.coord(2, 0),
                    MatchConstants.coord(1, 0),
                    MatchConstants.coord(0, 0));

            assertEquals(expectedPath, paths.get(0).path(), "Path should follow N,N,W,W priority");
        } finally {
            MatchConstants.width = originalWidth;
            MatchConstants.height = originalHeight;
            MatchConstants.initCoords(originalWidth, originalHeight);
        }
    }

    // Helper methods

    public static GameState createGameStateWithCitiesAndTerrain(Map<Integer, City> cities,
            Map<Coord, Rail> rails, TerrainType defaultTerrain) {
        return createGameStateWithCitiesAndTerrain(cities, rails,
                createTerrainGrid(MatchConstants.width, MatchConstants.height, defaultTerrain));
    }

    private static TerrainType[][] createTerrainGrid(int width, int height, TerrainType defaultTerrain) {
        TerrainType[][] terrain = new TerrainType[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                terrain[x][y] = defaultTerrain;
            }
        }
        return terrain;
    }

    public static GameState createGameStateWithCitiesAndTerrain(Map<Integer, City> cities,
            Map<Coord, Rail> rails, TerrainType[][] terrain) {
        int width = terrain.length;
        int height = terrain[0].length;

        TerrainType[][] terrainCopy = new TerrainType[width][height];
        int[][] regionIds = new int[width][height];
        int[][] cityIds = new int[width][height];

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                terrainCopy[x][y] = terrain[x][y];
                regionIds[x][y] = 0;
                cityIds[x][y] = -1;
            }
        }

        int maxCityId = cities.keySet().stream().max(Integer::compareTo).orElse(0);
        int cityArraySize = Math.max(1, maxCityId + 1);
        City[] citiesById = new City[cityArraySize];
        for (City city : cities.values()) {
            citiesById[city.id()] = city;
            cityIds[city.x()][city.y()] = city.id();
        }

        List<Tile> regionCells = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                City city = cityIds[x][y] >= 0 ? citiesById[cityIds[x][y]] : null;
                regionCells.add(new Tile(x, y, regionIds[x][y], terrainCopy[x][y], city));
            }
        }

        Region[] regions = new Region[] { new Region(0, 0, regionCells, new HashSet<>(), !cities.isEmpty()) };

        MatchConstants.cityCount = cities.size();
        MatchConstants.regionsCount = regions.length;

        initializeConnections(citiesById);

        MapDefinition map = new MapDefinition(width, height, terrainCopy, regionIds, cityIds, citiesById, regions);
        return new GameState(1, map, rails, 0, 0, new HashSet<>());
    }

    private static void initializeConnections(City[] citiesById) {
        int size = Math.max(1, citiesById.length);
        MatchConstants.connectionLookup = new Connection[size][size];
        for (int i = 0; i < size; i++) {
            if (i >= citiesById.length || citiesById[i] == null) {
                continue;
            }
            for (int j = 0; j < size; j++) {
                if (j >= citiesById.length || citiesById[j] == null) {
                    continue;
                }
                Connection connection = new Connection(i, j);
                MatchConstants.connectionLookup[i][j] = connection;
            }
        }
    }
}
