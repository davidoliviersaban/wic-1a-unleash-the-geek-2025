import java.util.*;
import java.io.*;

class Coord {
    public final int x;
    public final int y;

    public Coord(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public String toString() {
        return x + " " + y;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Coord)) return false;
        Coord c = (Coord) o;
        return x == c.x && y == c.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }
}

class Connection {
    public final int fromId;
    public final int toId;

    public Connection(int fromId, int toId) {
        this.fromId = fromId;
        this.toId = toId;
    }
}

class Tile {
    public int regionId;
    public int type;
    public int tracksOwner;
    public boolean inked;
    public int instability;
    public List<Connection> partOfActiveConnections;

    public Tile(int regionId, int type) {
        this.regionId = regionId;
        this.type = type;
        this.tracksOwner = -1;
        this.inked = false;
        this.instability = 0;
        this.partOfActiveConnections = new ArrayList<>();
    }
}

class Town {
    public int id;
    public Coord coord;
    public List<Integer> desiredConnections;

    public Town(int id, Coord coord, List<Integer> desiredConnections) {
        this.id = id;
        this.coord = coord;
        this.desiredConnections = desiredConnections;
    }
}

class Grid {
    public int width;
    public int height;
    public List<Tile> tiles;

    public Grid(int width, int height) {
        this.width = width;
        this.height = height;
        this.tiles = new ArrayList<>();
    }

    public Tile get(Coord coord) {
        return get(coord.x, coord.y);
    }

    public Tile get(int x, int y) {
        return tiles.get(y * width + x);
    }
}

class Region {
    public int id;
    public int instability;
    public boolean inked;
    public List<Coord> coords;
    public boolean hasTown;

    public Region(int id) {
        this.id = id;
        this.instability = 0;
        this.inked = false;
        this.coords = new ArrayList<>();
        this.hasTown = false;
    }
}

class Game {
    private int myId;
    private Grid grid;
    private List<Town> towns;
    private Map<Integer, Region> regionById;
    private int myScore;
    private int foeScore;
    private Scanner in;

    public Game() {
        in = new Scanner(System.in);
    }

    public Region getRegionAt(Coord coord) {
        return regionById.get(grid.get(coord).regionId);
    }

    public void init() {
        myId = in.nextInt();
        int width = in.nextInt();
        int height = in.nextInt();
        regionById = new HashMap<>();
        towns = new ArrayList<>();
        grid = new Grid(width, height);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int regionId = in.nextInt();
                int type = in.nextInt();
                Tile tile = new Tile(regionId, type);
                grid.tiles.add(tile);

                regionById.putIfAbsent(regionId, new Region(regionId));
                Region region = regionById.get(regionId);
                Coord coord = new Coord(x, y);
                region.coords.add(coord);
            }

        }

        int townCount = in.nextInt();
        for (int i = 0; i < townCount; i++) {
            int townId = in.nextInt();
            int townX = in.nextInt();
            int townY = in.nextInt();
            String desiredConnectionsStr = in.next();
            List<Integer> desiredConnections = new ArrayList<>();
            if (!desiredConnectionsStr.equals("x")) {
                for (String s : desiredConnectionsStr.split(",")) {
                    desiredConnections.add(Integer.parseInt(s));
                }
            }
            Coord coord = new Coord(townX, townY);
            Town town = new Town(townId, coord, desiredConnections);
            towns.add(town);
            getRegionAt(coord).hasTown = true;
        }
    }

    public void parse() {
        myScore = in.nextInt();
        foeScore = in.nextInt();
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                int tracksOwner = in.nextInt();
                int instability = in.nextInt();
                String inkedStr = in.next();
                String partOfActiveConnectionsStr = in.next();
                boolean inked = !inkedStr.equals("0");
                List<Connection> connections = new ArrayList<>();
                if (!partOfActiveConnectionsStr.equals("x")) {
                    for (String conn : partOfActiveConnectionsStr.split(",")) {
                        String[] ids = conn.split("-");
                        connections.add(new Connection(Integer.parseInt(ids[0]), Integer.parseInt(ids[1])));
                    }
                }
                Tile tile = grid.get(x, y);
                tile.tracksOwner = tracksOwner;
                tile.inked = inked;
                tile.instability = instability;
                tile.partOfActiveConnections = connections;
            }
        }
    }

    public void gameTurn() {
        List<String> actions = new ArrayList<>();
        
        // TODO: Game logic here
     
        
        if (!actions.isEmpty()) {
            System.out.println(String.join(";", actions));
        } else {
            System.out.println("WAIT");
        }
    }
}

/**
 * Connect towns with your train tracks and disrupt the opponent's.
 **/
class Player {
    public static void main(String[] args) {
        Game game = new Game();
        game.init();
        while (true) {
            game.parse();
            game.gameTurn();
        }
    }
}