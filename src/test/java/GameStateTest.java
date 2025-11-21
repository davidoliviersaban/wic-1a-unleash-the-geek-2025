import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.Test;

public class GameStateTest {

    @Test
    void canBuildAtReturnsFalseWhenRailAlreadyPresent() {
        int width = 2;
        int height = 2;
        MatchConstants.width = width;
        MatchConstants.height = height;
        MatchConstants.initCoords(width, height);

        TerrainType[][] terrain = new TerrainType[width][height];
        int[][] regionIds = new int[width][height];
        int[][] cityIds = new int[width][height];
        List<Tile> regionTiles = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                terrain[x][y] = TerrainType.PLAIN;
                regionIds[x][y] = 0;
                cityIds[x][y] = -1;
                regionTiles.add(new Tile(x, y, 0, TerrainType.PLAIN, null));
            }
        }

        Region[] regions = new Region[] { new Region(0, 0, regionTiles, new HashSet<>(), false) };
        City[] citiesById = new City[0];

        MapDefinition map = new MapDefinition(width, height, terrain, regionIds, cityIds, citiesById, regions);

        Map<Coord, Rail> rails = new HashMap<>();
        Coord occupied = MatchConstants.coord(0, 0);
        rails.put(occupied, new Rail(0, 0, RailOwner.ME));

        GameState gs = new GameState(0, map, rails, 0, 0, new HashSet<>());

        assertFalse(gs.canBuildAt(occupied), "canBuildAt must return false when a rail already occupies the coord");
        Coord free = MatchConstants.coord(1, 1);
        assertTrue(gs.canBuildAt(free), "canBuildAt should still allow building on empty coords in stable regions");
    }
}
