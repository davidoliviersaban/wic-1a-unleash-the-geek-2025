import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DisruptLogicTest {

    private SimpleAI ai;

    @BeforeEach
    void setUp() {
        ai = new SimpleAI();
    }

    @Test
    void testDisruptReturnsNullWhenAllRegionsHaveCities() {
        Connection shared = new Connection(0, 1);
        GameState gs = buildGameState(
                new int[][] { { 0, 0 }, { 1, 1 } },
                Map.of(0, true, 1, true),
                Map.of(0, Set.of(shared), 1, Set.of(shared)),
                Map.of(),
                List.of(
                        rail(0, 0, RailOwner.ME, shared),
                        rail(1, 0, RailOwner.OPPONENT, shared)),
                new HashSet<>(Set.of(shared)));

        assertNull(ai.getDisruptAction(gs), "No disrupt action should be chosen when every region is protected");
    }

    @Test
    void testDisruptIgnoresRegionsWithCitiesWhenAlternativesExist() {
        GameState gs = buildGameState(
                new int[][] { { 0, 0 }, { 1, 1 } },
                Map.of(0, true, 1, false),
                Map.of(),
                Map.of(),
                List.of(
                        rail(0, 0, RailOwner.OPPONENT),
                        rail(1, 0, RailOwner.OPPONENT)),
                new HashSet<>());

        Action action = ai.getDisruptAction(gs);
        assertNotNull(action);
        assertEquals(1, action.id(), "Region without a city should be disrupted even if another region has more rails");
    }

    @Test
    void testDisruptPrefersRegionWithMostOpponentRailsWhenNoConnections() {
        GameState gs = buildGameState(
                new int[][] { { 0 }, { 0 }, { 1 }, { 1 } },
                Map.of(),
                Map.of(),
                Map.of(),
                List.of(
                        rail(0, 0, RailOwner.OPPONENT),
                        rail(1, 0, RailOwner.OPPONENT),
                        rail(2, 0, RailOwner.OPPONENT)),
                new HashSet<>());

        Action action = ai.getDisruptAction(gs);
        assertNotNull(action);
        assertEquals(ActionType.DISRUPT, action.type());
        assertEquals(0, action.id(),
                "Region with the most opponent rails should be disrupted when no connections exist");
    }

    @Test
    void testDisruptPrefersRegionWithHighestConnectionPenalty() {
        Connection connHigh = new Connection(0, 1);
        Connection connLow = new Connection(2, 3);

        GameState gs = buildGameState(
                new int[][] { { 0, 0 }, { 1, 1 } },
                Map.of(),
                Map.of(0, Set.of(connHigh), 1, Set.of(connLow)),
                Map.of(),
                List.of(
                        rail(0, 0, RailOwner.OPPONENT, connHigh),
                        rail(0, 1, RailOwner.OPPONENT, connHigh),
                        rail(1, 0, RailOwner.OPPONENT, connLow)),
                new HashSet<>(Arrays.asList(connHigh, connLow)));

        Action action = ai.getDisruptAction(gs);
        assertNotNull(action);
        assertEquals(0, action.id(), "Region with the highest negative connection value should be disrupted");
    }

    @Test
    void testIncreasingInstabilityRemovesRailsAndConnections() {
        Connection conn = new Connection(0, 1);
        Map<Integer, Integer> instability = Map.of(0, MatchConstants.INSTABILITY_THRESHOLD - 1);
        Set<Connection> cachedConnections = new HashSet<>(Set.of(conn));
        GameState gs = buildGameState(
                new int[][] { { 0 }, { 1 } },
                Map.of(),
                Map.of(0, Set.of(conn)),
                instability,
                List.of(
                        rail(0, 0, RailOwner.OPPONENT, conn),
                        rail(1, 0, RailOwner.ME)),
                cachedConnections);

        Coord disruptedRailCoord = MatchConstants.coord(0, 0);
        GameState after = gs.increaseInstability(0);

        assertFalse(after.rails().containsKey(disruptedRailCoord), "Rails in the disrupted region should be removed");
        assertFalse(after.cachedConnections().contains(conn),
                "Connections belonging to disrupted rails should be removed from cache");
    }

    private GameState buildGameState(int[][] regionAssignments,
            Map<Integer, Boolean> regionsWithCities,
            Map<Integer, Set<Connection>> regionConnections,
            Map<Integer, Integer> regionInstability,
            List<RailSpec> railSpecs,
            Set<Connection> cachedConnections) {

        int width = regionAssignments.length;
        int height = regionAssignments[0].length;
        MatchConstants.width = width;
        MatchConstants.height = height;
        MatchConstants.initCoords(width, height);

        TerrainType[][] terrain = new TerrainType[width][height];
        int[][] regionIds = new int[width][height];
        int[][] cityIds = new int[width][height];
        City[] citiesById = new City[0];

        Map<Integer, List<Tile>> cellsByRegion = new HashMap<>();
        int maxRegionId = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int regionId = regionAssignments[x][y];
                maxRegionId = Math.max(maxRegionId, regionId);
                terrain[x][y] = TerrainType.PLAIN;
                regionIds[x][y] = regionId;
                cityIds[x][y] = -1;
                Tile tile = new Tile(x, y, regionId, TerrainType.PLAIN, null);
                cellsByRegion.computeIfAbsent(regionId, k -> new ArrayList<>()).add(tile);
            }
        }

        Region[] regions = new Region[maxRegionId + 1];
        for (int id = 0; id <= maxRegionId; id++) {
            List<Tile> cells = cellsByRegion.getOrDefault(id, new ArrayList<>());
            Set<Connection> connections = new HashSet<>(regionConnections.getOrDefault(id, Set.of()));
            boolean hasCity = regionsWithCities.getOrDefault(id, false);
            int instability = regionInstability.getOrDefault(id, 0);
            regions[id] = new Region(id, instability, cells, connections, hasCity);
        }

        Map<Coord, Rail> rails = new HashMap<>();
        for (RailSpec spec : railSpecs) {
            Rail rail = new Rail(spec.x(), spec.y(), spec.owner());
            if (!spec.connections().isEmpty()) {
                rail.partOfActiveConnections = new ArrayList<>(spec.connections());
            }
            rails.put(MatchConstants.coord(spec.x(), spec.y()), rail);
        }

        MapDefinition map = new MapDefinition(width, height, terrain, regionIds, cityIds, citiesById, regions);
        return new GameState(1, map, rails, 0, 0, cachedConnections);
    }

    private RailSpec rail(int x, int y, RailOwner owner, Connection... connections) {
        List<Connection> list = connections == null || connections.length == 0 ? List.of() : Arrays.asList(connections);
        return new RailSpec(x, y, owner, list);
    }

    private record RailSpec(int x, int y, RailOwner owner, List<Connection> connections) {
    }
}
