import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.print.attribute.standard.MediaSize.NA;

class Player {

	// Easy to find switches
	public static final boolean isDebugOn = true;
	public static int ME;
	public static int OPP;
	public static int nbWait = 0;
	public static int nbDisrupt = 0;
	public static int nbBuild = 0;
	public static int nbNoBuild = 0;

	// Magic numbers

	// AIs
	private static AI ai = new SimpleAI();

	// Game constants, write them here once for all. The match constants however
	// should go in MatchConstants

	// Game variables
	private static GameState previousGameState;
	private static boolean stopGame = false;

	public static void main(String args[]) {

		Scanner in = new Scanner(System.in);
		initMatch(in);

		// game loop
		while (true) {

			GameState gs = initRound(in);

			List<Action> actions = ai.compute(gs);

			finalizeRound(actions, gs);

			out(actions);

		}
	}

	static void initMatch(Scanner in) {

		if (isDebugOn) {
			Print.debug("Starting the match !");
			if (ai != null) {
				ai.printAI();
			}
		}

		ME = in.nextInt(); // 0 or 1
		OPP = 1 - ME;
		int width = in.nextInt(); // map size
		int height = in.nextInt();
		MatchConstants.height = height;
		MatchConstants.width = width;
		MatchConstants.initCoords(width, height);

		// Parse terrain grid
		Tile[][] tiles = new Tile[width][height];

		Set<Integer> regionIds = new HashSet<>();

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int regionId = in.nextInt();
				int type = in.nextInt(); // 0 (PLAINS), 1 (RIVER), 2 (MOUNTAIN), 3 (POI)

				TerrainType terrainType = switch (type) {
					case 0 -> TerrainType.PLAIN;
					case 1 -> TerrainType.RIVER;
					case 2 -> TerrainType.MOUNTAIN;
					case 3 -> TerrainType.POI;
					default -> TerrainType.PLAIN; // POI treated as plain for now
				};

				tiles[x][y] = new Tile(x, y, regionId, terrainType, null);
				regionIds.add(regionId);
			}
		}

		// Parse cities (towns)
		int townCount = in.nextInt();
		City[] citiesById = new City[townCount];

		for (int i = 0; i < townCount; i++) {
			int townId = in.nextInt();
			int townX = in.nextInt();
			int townY = in.nextInt();
			String desiredConnections = in.next(); // comma-separated town ids e.g. 0,1,2,3

			List<Integer> desiredCityIds = new ArrayList<>();
			if (!desiredConnections.equals("x")) {
				String[] parts = desiredConnections.split(",");
				for (String part : parts) {
					desiredCityIds.add(Integer.parseInt(part));
				}
			}

			int regionId = tiles[townX][townY].regionId();
			City city = new City(townId, townX, townY, regionId, desiredCityIds);
			citiesById[townId] = city;

			// Embed city in terrain
			tiles[townX][townY] = new Tile(townX, townY,
					tiles[townX][townY].regionId(), tiles[townX][townY].type(), city);
		}

		MatchConstants.cityCount = townCount;
		MatchConstants.regionsCount = regionIds.size();

		// Create initial MapDefinition
		TerrainType[][] terrainType = new TerrainType[width][height];
		int[][] regionIdArray = new int[width][height];
		int[][] cityIdArray = new int[width][height];
		Region[] regions = new Region[MatchConstants.regionsCount];

		// Collect cells for each region
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int regionId = tiles[x][y].regionId();
				if (regions[regionId] == null) {
					regions[regionId] = new Region(regionId, 0, new ArrayList<>(), new HashSet<>(),
							tiles[x][y].hasCity());
				}
				regions[regionId].cells().add(tiles[x][y]);
				if (tiles[x][y].hasCity()) {
					regions[regionId] = new Region(regionId, regions[regionId].instability(),
							regions[regionId].cells(), regions[regionId].connections(), true);
				}
			}
		}

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				terrainType[x][y] = tiles[x][y].type();
				regionIdArray[x][y] = tiles[x][y].regionId();
				cityIdArray[x][y] = tiles[x][y].city() != null ? tiles[x][y].city().id() : -1;
			}
		}

		MapDefinition mapDef = new MapDefinition(width, height, terrainType, regionIdArray, cityIdArray,
				citiesById, regions);

		MatchConstants.connectionLookup = new Connection[townCount][townCount];
		for (City city1 : citiesById) {
			if (city1 == null)
				continue;
			for (City city2 : citiesById) {
				if (city2 == null)
					continue;
				Connection connection = new Connection(city1.id(), city2.id());
				MatchConstants.connectionLookup[city1.id()][city2.id()] = connection;
				MatchConstants.connectionLookup[city2.id()][city1.id()] = connection;
			}
		}
		// Create initial game state
		previousGameState = new GameState(0, mapDef, new HashMap<>(), 0, 0, Set.of());

		Time.startRoundTimer();
		MatchConstants.print();
	}

	private static Connection parseConnectionToken(String token) {
		if (token == null) {
			return null;
		}
		String trimmed = token.trim();
		if (trimmed.isEmpty() || "x".equalsIgnoreCase(trimmed)) {
			return null;
		}
		String[] ids = trimmed.split("-");
		if (ids.length != 2) {
			return null;
		}
		try {
			int from = Integer.parseInt(ids[0]);
			int to = Integer.parseInt(ids[1]);
			return MatchConstants.connection(from, to);
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	static GameState initRound(Scanner in) {

		// Read scores
		int myScore = in.nextInt();
		Time.startRoundTimer();

		GameState result = previousGameState.nextRound();
		Time.debugDuration("Starting initRound");

		int foeScore = in.nextInt();

		// Update game state from previous round
		result = result.withScores(myScore, foeScore);

		// Parse grid state
		Map<Coord, Rail> rails = new HashMap<>(MatchConstants.width * MatchConstants.height);
		Set<Connection> resetCachedConnectionsSet = new TreeSet<>();

		@SuppressWarnings("unchecked")
		Set<Connection>[] regionConnections = new Set[MatchConstants.regionsCount];
		int[] regionInstability = new int[MatchConstants.regionsCount];

		Time.debugDuration("Starting reading input");

		for (int y = 0; y < MatchConstants.height; y++) {
			for (int x = 0; x < MatchConstants.width; x++) {
				int tracksOwner = in.nextInt();
				int instability = in.nextInt();
				in.nextInt(); // Skip inked flag (not used)
				String partOfActiveConnectionStr = in.next();
				Coord coord = MatchConstants.coord(x, y);

				// Recall instability
				int regionId = result.map().regionIdAt(x, y);
				regionInstability[regionId] = instability;

				if (tracksOwner >= 0) {
					RailOwner owner = RailOwner.NONE;
					if (tracksOwner == ME) {
						owner = RailOwner.ME;
					} else if (tracksOwner == OPP) {
						owner = RailOwner.OPPONENT;
					} else {
						owner = RailOwner.CONTESTED;
					}

					Rail rail = new Rail(x, y, owner);
					rail.partOfActiveConnections = new ArrayList<>();
					rails.put(coord, rail);

					if (partOfActiveConnectionStr.charAt(0) != 'x') {
						String[] connections = partOfActiveConnectionStr.split(",");
						for (String conn : connections) {
							Connection connection = MatchConstants.connection(
									Integer.parseInt(conn.split("-")[0]),
									Integer.parseInt(conn.split("-")[1]));
							rail.partOfActiveConnections.add(connection);
							resetCachedConnectionsSet.add(connection);

							if (regionConnections[regionId] == null) {
								regionConnections[regionId] = new HashSet<>();
							}
							regionConnections[regionId].add(connection);
						}
					}
					// Print.debug("InitRound: adding rail at (" + x + "," + y + ") owned by " +
					// owner
					// + " with " + rail.partOfActiveConnections.size() + " active connections");
				}
			}
		}

		Time.debugDuration("Update regions");

		for (int i = 0; i < MatchConstants.regionsCount; i++) {
			Region oldRegion = result.map().regions()[i];
			if (oldRegion == null) {
				oldRegion = new Region(i, 0, new ArrayList<>(), new HashSet<>(), false);
			}
			Set<Connection> connections = regionConnections[i] != null ? regionConnections[i] : new HashSet<>();
			int instability = regionInstability[i] != 0 ? regionInstability[i] : oldRegion.instability();
			result.map().regions()[i] = new Region(oldRegion.id(), instability, oldRegion.cells(), connections,
					oldRegion.hasCity());
		}

		Time.debugDuration("Finished reading input");

		// Create updated game state
		result = new GameState(result.round(), result.map(), rails, myScore, foeScore,
				resetCachedConnectionsSet);
		Print.debug("Cached connections after initRound: " + resetCachedConnectionsSet.stream()
				.map(c -> c.fromId() + "-" + c.toId()).collect(java.util.stream.Collectors.joining(", ")));
		Time.debugDuration("Finished initround");

		return result;

	}

	private static void finalizeRound(List<Action> actions, GameState gs) {

		if (!stopGame) {

			previousGameState = gs;

			if (isDebugOn) {

				Time.debugDuration("Total round duration");
				if (gs.round() > 1) {
					Time.addRoundDuration();
					Time.debugAverageRoundDuration(gs.round());
				}
			}

		}

	}

	private static void out(List<Action> actions) {
		if (stopGame) {
			System.out.println("Failure!");
		} else {
			String output = "MESSAGE nbW: " + Player.nbWait + " nbD: " + Player.nbDisrupt + " NbBuild: "
					+ Player.nbBuild + " NbNoBuild: " + Player.nbNoBuild + ";";
			output += actions.stream().map(Action::toString).collect(Collectors.joining(";"));
			System.out.println(output);
		}

	}

}

// Stores stuff which is not going to change for the whole match, but could
// change from one match to another
class MatchConstants {

	public static Connection[][] connectionLookup;
	public static int cityCount;
	public static int regionsCount;
	public static final int INSTABILITY_THRESHOLD = 4;
	public static final int MAX_ACTIONS_PER_TURN = 3;
	public static int height;
	public static int width;
	private static Coord[][] coords;

	public static Coord coord(int x, int y) {
		return coords[x][y];
	}

	public static void initCoords(int width, int height) {
		coords = new Coord[width][height];
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				coords[x][y] = new Coord(x, y);
			}
		}
	}

	public static boolean isValid(int x, int y) {
		return x >= 0 && x < MatchConstants.width && y >= 0 && y < MatchConstants.height;
	}

	public static void print() {
		Print.debugForInput("MatchConstatns height: " + height + " width:" + width);
	}

	public static Connection connection(int fromId, int toId) {
		if (connectionLookup == null || fromId < 0 || fromId >= connectionLookup.length || toId < 0
				|| toId >= connectionLookup[fromId].length) {
			return null;
		}
		return connectionLookup[fromId][toId];
	}

}

class Time {
	// Time constants
	private static final int msToNano = 1_000_000;
	private static final int maxRoundTime = 50 * msToNano; // 50 ms max to answer
	private static final int roundTimeMargin = 15 * msToNano;
	private static final int maxFirstRoundTime = 1000 * msToNano; // 1 s max to answer for first turn only
	private static final int firstRoundTimeMargin = 50 * msToNano;
	private static final int maxRoundTimeWithMargin = maxRoundTime - roundTimeMargin;
	private static final int maxFirstRoundTimeWithMargin = maxFirstRoundTime - firstRoundTimeMargin;
	public static boolean noTimeLimit = false;

	// Time variables
	private static long roundStartTime;
	private static long totalRoundDuration = 0;

	public static void startRoundTimer() {
		roundStartTime = System.nanoTime();
	}

	public static boolean isTimeLeft(boolean isFirstTurn) {
		return getRoundDurationNano() < maxRoundTimeWithMargin
				|| (isFirstTurn && getRoundDurationNano() < maxFirstRoundTimeWithMargin) || noTimeLimit;
	}

	public static long getRoundDurationNano() {
		return (System.nanoTime() - roundStartTime);
	}

	public static long getRoundDuration() {
		return (System.nanoTime() - roundStartTime) / msToNano;
	}

	public static void addRoundDuration() {
		totalRoundDuration += getRoundDurationNano();
	}

	public static void debugDuration(String message) {
		Print.debug(message + ": " + getRoundDuration() + " ms");
	}

	public static void debugAverageRoundDuration(int round) {
		if (round > 1) {
			Print.debug("Average duration: " + Print.formatDoubleFixedLenghtAFterComma(-1, 2,
					Time.totalRoundDuration / (((double) round - 1) * msToNano)));
		}
	}

}

class Print {

	public static void debug(String message) {
		if (Player.isDebugOn) {
			System.err.println(message);
		}
	}

	public static void debugForced(String message) {
		System.err.println(message);
	}

	private static final String debugStartLine = "\"";
	private static final String debugEndLine = "\",";
	public static final String debugSep = " ";

	// Debug for later input in tests
	public static void debugForInput(String message) {
		debug(debugStartLine + message + debugEndLine);
	}

	public static String formatIntFixedLenght(int lenght, int i) {
		return String.format("%0" + lenght + "d", i);
	}

	public static String formatDoubleFixedLenghtAFterComma(int lenghtBeforeComma, int lenghtAfterComma, double d) {
		return String.format("%" + (lenghtBeforeComma == -1 ? "" : lenghtBeforeComma) + "." + lenghtAfterComma + "f",
				d);
	}

	// Use with caution if the string is split later... extra whitespaces could
	// cause issues
	public static String formatStringFixedLength(int length, String s) {
		return String.format("%1$-" + length + "s", s);
	}

}

// Consolidated model (single-file constraint): all domain records & enums
// below.

enum ActionType {
	PLACE_TRACKS, DISRUPT, AUTOPLACE, MESSAGE, WAIT
}

record Coord(int x, int y) {
	boolean isInside(int width, int height) {
		return x >= 0 && x < width && y >= 0 && y < height;
	}
}

record Connection(int fromId, int toId) implements Comparable<Connection> {
	@Override
	public int compareTo(Connection other) {
		if (this.fromId == other.fromId && this.toId() == other.toId()
				|| this.fromId == other.toId && this.toId == other.fromId) {
			return 0;
		} else if (this.fromId != other.fromId) {
			return Integer.compare(this.fromId, other.fromId);
		}
		return Integer.compare(this.toId, other.toId);
	}
}

enum TerrainType {
	PLAIN, RIVER, MOUNTAIN, POI;

	int buildCost() {
		return switch (this) {
			case PLAIN -> 1;
			case RIVER -> 2;
			case MOUNTAIN, POI -> 3;
		};
	}
}

record Tile(int x, int y, int regionId, TerrainType type, City city) {

	int buildCost() {
		return switch (type) {
			case PLAIN -> 1;
			case RIVER -> 2;
			case MOUNTAIN, POI -> 3;
		};
	}

	boolean hasCity() {
		return city != null;
	}

	boolean hasPOI() {
		return type == TerrainType.POI;
	}
}

record City(int id, int x, int y, int regionId, List<Integer> desiredCityIds) {
	boolean desires(int otherCityId) {
		return desiredCityIds != null && desiredCityIds.contains(otherCityId);
	}
}

record Region(int id, int instability, List<Tile> cells, Set<Connection> connections, boolean hasCity) {
	boolean isInstable() {
		return instability >= MatchConstants.INSTABILITY_THRESHOLD;
	}

	Region increaseInstability() {
		return new Region(id, instability + 1, cells, connections, hasCity);
	}

}

enum RailOwner {
	ME, OPPONENT, CONTESTED, NONE;

	boolean isOwned() {
		return this == ME || this == OPPONENT;
	}
}

class Rail {
	int x;
	int y;
	RailOwner owner;
	List<Connection> partOfActiveConnections;

	Rail(int x, int y, RailOwner owner) {
		this.x = x;
		this.y = y;
		this.owner = owner;
	}

	int x() {
		return x;
	}

	int y() {
		return y;
	}

	RailOwner owner() {
		return owner;
	}

	boolean isContested() {
		return owner == RailOwner.CONTESTED;
	}
}

record MapDefinition(
		int width,
		int height,
		TerrainType[][] terrainType,
		int[][] regionId,
		int[][] cityId,
		City[] citiesById,
		Region[] regions) {

	MapDefinition {
		Objects.requireNonNull(terrainType);
		Objects.requireNonNull(regionId);
		Objects.requireNonNull(cityId);
		Objects.requireNonNull(citiesById);
		Objects.requireNonNull(regions);
	}

	int regionIdAt(int x, int y) {
		return regionId[x][y];
	}

	int regionIdAt(Coord coord) {
		return regionIdAt(coord.x(), coord.y());
	}

	City cityAt(int x, int y) {
		int id = cityId[x][y];
		return id >= 0 ? citiesById[id] : null;
	}

	City cityAt(Coord coord) {
		return cityAt(coord.x(), coord.y());
	}

	City cityById(int id) {
		return citiesById[id];
	}

	int buildCostAt(int x, int y) {
		return terrainType[x][y].buildCost();
	}

	TerrainType terrainAt(Coord coord) {
		return terrainType[coord.x()][coord.y()];
	}
}

record Action(ActionType type, Coord coord1, Coord coord2, int id) implements Comparable<Action> {
	static Action buildRail(int x, int y) {
		return new Action(ActionType.PLACE_TRACKS, MatchConstants.coord(x, y), null, -1);
	}

	static Action autoPlace(int x1, int y1, int x2, int y2) {
		return new Action(ActionType.AUTOPLACE, MatchConstants.coord(x1, y1), MatchConstants.coord(x2, y2), -1);
	}

	static Action disruptRegion(int id) {
		Player.nbDisrupt++;
		return new Action(ActionType.DISRUPT, null, null, id);
	}

	static Action waitAction() {
		Player.nbWait++;
		return new Action(ActionType.WAIT, null, null, -1);
	}

	@Override
	public int compareTo(Action other) {
		return toString().compareTo(other.toString());
	}

	@Override
	public String toString() {
		return switch (type) {
			case PLACE_TRACKS -> ActionType.PLACE_TRACKS + " " + coord1.x() + " " + coord1.y();
			case AUTOPLACE ->
				ActionType.AUTOPLACE + " " + coord1.x() + " " + coord1.y() + " " + coord2.x() + " " + coord2.y();
			case DISRUPT -> ActionType.DISRUPT + " " + id;
			case MESSAGE -> ActionType.MESSAGE + " insert a message here";
			case WAIT -> "WAIT";
		};
	}
}

record GameState(int round, MapDefinition map, Map<Coord, Rail> rails, int myScore, int opponentScore,
		Set<Connection> cachedConnections) {

	GameState {
		Objects.requireNonNull(map);
		Objects.requireNonNull(rails);
		Objects.requireNonNull(cachedConnections);
	}

	GameState nextRound() {
		Print.debug("Advancing to round " + (round() + 1));
		return new GameState(round() + 1, map(), rails(), myScore(), opponentScore(), cachedConnections());
	}

	GameState withRails(List<Coord> coords, RailOwner owner) {
		Map<Coord, Rail> newRails = new HashMap<>(rails);
		for (Coord c : coords) {
			Rail existing = newRails.get(c);
			Rail newRail = existing == null ? new Rail(c.x(), c.y(), owner) : resolveConflict(existing, owner);
			newRails.put(c, newRail);
		}
		return new GameState(round, map, newRails, myScore, opponentScore, cachedConnections);
	}

	private Rail resolveConflict(Rail existing, RailOwner incoming) {
		if (existing.owner() == incoming)
			return existing;
		return new Rail(existing.x, existing.y, RailOwner.CONTESTED);
	}

	Map<Coord, Rail> railsInRegionAsMap(int regionId) {
		Map<Coord, Rail> filtered = new HashMap<>();
		for (var e : rails.entrySet()) {
			int rid = map.regionIdAt(e.getKey().x(), e.getKey().y());
			// BugFix: was the opposite logic
			if (rid == regionId)
				filtered.put(e.getKey(), e.getValue());
		}
		return filtered;
	}

	boolean canBuildAt(Coord c) {
		int regionId = map.regionIdAt(c.x(), c.y());
		if (map().regions()[regionId].isInstable() || map().regions()[regionId].instability() >= 2)
			return false;

		// BugFix: adding check on rails presence here that was missing
		return !(map().cityAt(c.x(), c.y()) != null);
	}

	GameState increaseInstability(int regionId) {
		Region[] newRegions = map().regions().clone();
		if (regionId >= 0 && regionId < newRegions.length) {
			newRegions[regionId] = newRegions[regionId].increaseInstability();
			if (newRegions[regionId].isInstable()) {
				Map<Coord, Rail> railsToRemove = railsInRegionAsMap(regionId);
				Map<Coord, Rail> newRails = new HashMap<>(rails);
				for (Coord c : railsToRemove.keySet()) {
					newRails.remove(c);
					Rail railToRemove = railsToRemove.get(c);
					// TODO: Fix Me Stupid way to update cached connections
					if (railToRemove != null && railToRemove.partOfActiveConnections != null) {
						for (Connection conn : railToRemove.partOfActiveConnections) {
							cachedConnections.remove(conn);
						}
					}
				}
				return new GameState(round, map, newRails, myScore, opponentScore, cachedConnections);
			}
		}
		return new GameState(round, map, rails, myScore, opponentScore, cachedConnections);
	}

	GameState withScores(int my, int opp) {
		return new GameState(round, map, rails, my, opp, cachedConnections);
	}

	GameState withCachedConnections(Set<Connection> connections) {
		return new GameState(round, map, rails, myScore, opponentScore, connections);
	}

	int regionIdAt(Coord coord) {
		return map.regionIdAt(coord);
	}

	City cityAt(Coord coord) {
		return map.cityAt(coord);
	}

	RailOwner ownerAt(Coord coord) {
		Rail rail = rails.get(coord);
		return rail != null ? rail.owner() : RailOwner.NONE;
	}

	// For testing purposes
	static GameState createInitial(int width, int height) {
		Tile[][] tiles = new Tile[width][height];
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				tiles[x][y] = new Tile(x, y, -1, TerrainType.PLAIN, null);
			}
		}
		TerrainType[][] terrainTypeArray = new TerrainType[width][height];
		int[][] regionIdArray = new int[width][height];
		int[][] cityIdArray = new int[width][height];
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				terrainTypeArray[x][y] = tiles[x][y].type();
				regionIdArray[x][y] = tiles[x][y].regionId();
				cityIdArray[x][y] = tiles[x][y].city() != null ? tiles[x][y].city().id() : -1;
			}
		}
		MapDefinition mapDef = new MapDefinition(width, height, terrainTypeArray, regionIdArray, cityIdArray,
				new City[0], new Region[0]);
		return new GameState(1, mapDef, Map.of(), 0, 0, Set.of());
	}
}

// Pathfinding using BFS for shortest rail paths

record CityConnection(City from, City to, List<Coord> path, int distance) {
}

// NAMOA* (Non-dominated Archive Multi-Objective A*) pathfinding

record PathCost(int distance, int buildCost, int instability) implements Comparable<PathCost> {
	PathCost add(int distInc, int costInc, int instabilityInc) {
		return new PathCost(distance + distInc, buildCost + costInc, instability + instabilityInc);
	}

	boolean dominates(PathCost other) {
		// Simplify the dominance check
		// return this.buildCost < other.buildCost;
		return this.distance <= other.distance && this.buildCost <= other.buildCost
				&& (this.distance < other.distance || this.buildCost < other.buildCost)
				&& this.instability <= other.instability;
	}

	@Override
	public int compareTo(PathCost other) {
		int sumThis = this.distance + this.buildCost;
		int sumOther = other.distance + other.buildCost;
		return Integer.compare(sumThis, sumOther);
	}
}

record NAMOANode(Coord coord, PathCost cost, PathCost heuristic, NAMOANode parent, long insertionOrder)
		implements Comparable<NAMOANode> {
	PathCost totalCost() {
		return new PathCost(cost.distance() + heuristic.distance(), cost.buildCost() + heuristic.buildCost(),
				cost.instability() + heuristic.instability());
	}

	@Override
	public int compareTo(NAMOANode other) {
		int comparison = this.totalCost().compareTo(other.totalCost());
		if (comparison != 0) {
			return comparison;
		}
		return Long.compare(this.insertionOrder, other.insertionOrder);
	}
}

class NAMOAStar {

	/**
	 * Finds non-dominated paths from a start city to multiple target cities using *
	 * NAMOA*. Returns a map from target city ID to a list of non-dominated paths.
	 * Optimized version with early termination and visited tracking.
	 */
	static Map<Integer, List<NAMOAPath>> findPaths(GameState gs, City start, List<City> targets) {
		Map<Integer, List<NAMOAPath>> results = new HashMap<>();
		for (City target : targets) {
			results.put(target.id(), new ArrayList<>());
		}

		Set<Integer> targetIds = targets.stream().map(City::id).collect(java.util.stream.Collectors.toSet());
		Set<Integer> foundTargets = new HashSet<>();
		Coord startCoord = MatchConstants.coord(start.x(), start.y());

		// Open list (priority queue)
		java.util.PriorityQueue<NAMOANode> open = new java.util.PriorityQueue<>();

		// Non-dominated archive per coordinate - limit size for performance
		Map<Coord, List<PathCost>> archive = new HashMap<>();

		// Closed set to avoid reprocessing
		Set<Coord> closed = new HashSet<>();

		// Initial node
		PathCost initialCost = new PathCost(0, 0, 0);
		PathCost initialHeuristic = computeHeuristic(gs, startCoord, targets);
		long insertionCounter = 0;
		NAMOANode startNode = new NAMOANode(startCoord, initialCost, initialHeuristic, null, insertionCounter++);
		open.add(startNode);

		int nodesExpanded = 0;
		final int MAX_NODES = 5000; // Limit search space for performance

		while (!open.isEmpty() && nodesExpanded < MAX_NODES) {
			NAMOANode current = open.poll();
			nodesExpanded++;

			// Skip if already processed
			if (closed.contains(current.coord())) {
				continue;
			}
			closed.add(current.coord());

			// Check if dominated by archive
			if (isDominated(current.cost(), archive.get(current.coord()))) {
				continue;
			}

			// Check if we reached a target
			City reachedCity = gs.map().cityAt(current.coord().x(), current.coord().y());
			if (reachedCity != null && targetIds.contains(reachedCity.id())) {
				List<Coord> path = reconstructPath(current);
				NAMOAPath solution = new NAMOAPath(start, reachedCity, path, current.cost());
				addNonDominatedSolution(results.get(reachedCity.id()), solution);
				foundTargets.add(reachedCity.id());

				// Early termination if we found all targets with at least one path
				if (foundTargets.size() == targetIds.size()) {
					break;
				}
			}

			// Expand neighbors
			for (Coord neighbor : getNeighbors(gs, current.coord())) {
				if (closed.contains(neighbor)) {
					continue;
				}

				int edgeDistance = 1;
				int edgeCost = getMovementCost(gs, neighbor);
				int edgeInstability = getInstabilityCost(gs, neighbor);

				PathCost newCost = current.cost().add(edgeDistance, edgeCost, edgeInstability);

				// Check if dominated by archive at neighbor
				List<PathCost> neighborArchive = archive.get(neighbor);
				if (!isDominated(newCost, neighborArchive)) {
					PathCost newHeuristic = computeHeuristic(gs, neighbor, targets);
					NAMOANode newNode = new NAMOANode(neighbor, newCost, newHeuristic, current, insertionCounter++);
					open.add(newNode);

					// Update archive with size limit
					addToArchiveLimited(archive, neighbor, newCost, 3); // Max 3 non-dominated paths per cell
				}
			}
		}

		return results;
	}

	private static int getInstabilityCost(GameState gs, Coord neighbor) {
		int regionId = gs.regionIdAt(neighbor);
		Region region = gs.map().regions()[regionId];
		return region.instability();
	}

	private static PathCost computeHeuristic(GameState gs, Coord from, List<City> targets) {
		int minDistance = Integer.MAX_VALUE;
		int minCost = 0;
		int minInstability = 0;

		for (City target : targets) {
			int dist = Math.abs(from.x() - target.x()) + Math.abs(from.y() - target.y());
			if (dist < minDistance) {
				minDistance = dist;
				// Estimate cost (assuming average terrain)
				minCost = dist;
			}
		}

		return new PathCost(minDistance, minCost, minInstability);
	}

	private static boolean isDominated(PathCost cost, List<PathCost> archive) {
		if (archive == null || archive.isEmpty()) {
			return false;
		}

		for (PathCost archiveCost : archive) {
			if (archiveCost.dominates(cost)) {
				return true;
			}
		}
		return false;
	}

	private static void addToArchiveLimited(Map<Coord, List<PathCost>> archive, Coord coord, PathCost newCost,
			int maxSize) {
		List<PathCost> costs = archive.computeIfAbsent(coord, k -> new ArrayList<>());

		// Remove dominated costs
		costs.removeIf(existingCost -> newCost.dominates(existingCost));

		// Add if not dominated and under size limit
		if (!isDominated(newCost, costs)) {
			costs.add(newCost);
			// Keep only best paths if exceeding limit
			if (costs.size() > maxSize) {
				costs.sort(PathCost::compareTo);
				while (costs.size() > maxSize) {
					costs.remove(costs.size() - 1);
				}
			}
		}
	}

	private static void addNonDominatedSolution(List<NAMOAPath> solutions, NAMOAPath newSolution) {
		// Remove solutions dominated by the new one
		solutions.removeIf(existing -> newSolution.cost().dominates(existing.cost()));

		// Add if not dominated
		boolean dominated = false;
		for (NAMOAPath existing : solutions) {
			if (existing.cost().dominates(newSolution.cost())) {
				dominated = true;
				break;
			}
		}

		if (!dominated) {
			solutions.add(newSolution);
		}
	}

	private static List<Coord> getNeighbors(GameState gs, Coord coord) {
		List<Coord> neighbors = new ArrayList<>();
		// NORTH, EAST, SOUTH, WEST
		int[][] directions = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };

		for (int[] dir : directions) {
			int nx = coord.x() + dir[0];
			int ny = coord.y() + dir[1];

			if (MatchConstants.isValid(nx, ny)) {
				Coord neighbor = MatchConstants.coord(nx, ny);
				if (gs.canBuildAt(neighbor) || gs.map().cityAt(nx, ny) != null) {
					neighbors.add(neighbor);
				}
			}
		}

		return neighbors;
	}

	private static int getMovementCost(GameState gs, Coord coord) {
		// If there's already a rail, no cost to use it
		if (gs.rails().containsKey(coord)) {
			return 0;
		}
		// If it's a city, no cost
		if (gs.map().cityAt(coord.x(), coord.y()) != null) {
			return 0;
		}
		// Otherwise, terrain build cost
		return gs.map().terrainType()[coord.x()][coord.y()].buildCost();
	}

	private static List<Coord> reconstructPath(NAMOANode node) {
		List<Coord> path = new ArrayList<>();
		NAMOANode current = node;
		while (current != null) {
			path.add(0, current.coord());
			current = current.parent();
		}
		return path;
	}
}

record NAMOAPath(City from, City to, List<Coord> path, PathCost cost) {
	int distance() {
		return cost.distance();
	}

	int buildCost() {
		return cost.buildCost();
	}
}

interface AI {

	// Will compute an Action from a provided GameState which is going to stay
	// INTACT during the computation
	// This is the default entry point for an AI
	public default List<Action> computeIntact(GameState gs) {
		return compute(gs);
	}

	// Compute the action on the provided gs, *potentially* altering it during the
	// computation. At least it doesn't make an upfront copy.
	// Should only be used when:
	// 1) we need perf so we don't want to pay the copy() price or copy() is not
	// implemented, and
	// 2) we don't care about the gs being altered OR we know it's not going to be
	// altered by a specific implementation
	public List<Action> compute(GameState gs);

	// Nothing to print by default, can be overriden if needed, when the AI relies
	// on other things than just the turn's input gamestate...
	public default void printAIParameters() {
	}

	public default void printAI() {
		Print.debug("Using base AI: " + this.getClass().getName());
	}

	public default Action getDisruptAction(GameState gs) {
		Action result = null;
		Map<Connection, Integer> connectionWorthMap = new HashMap<>();

		Time.debugDuration("Disrupt action computation start");
		for (Connection conn : gs.cachedConnections()) {
			for (Rail rail : gs.rails().values()) {
				if (rail.partOfActiveConnections != null && rail.partOfActiveConnections.contains(conn)) {
					if (rail.owner() == RailOwner.ME) {
						connectionWorthMap.put(conn, connectionWorthMap.getOrDefault(conn, 0) + 1);
					} else if (rail.owner() == RailOwner.OPPONENT) {
						connectionWorthMap.put(conn, connectionWorthMap.getOrDefault(conn, 0) - 1);
					}
				}
			}
			// Print.debug("Connection from " + conn.fromId() + " to " + conn.toId() + " has
			// worth: "
			// + connectionWorthMap.getOrDefault(conn, 0));
		}

		double worstRegionValue = 0;
		List<Region> regionCandidateToDisrupt = new ArrayList<Region>();
		for (Region region : gs.map().regions()) {
			if (region.isInstable() || region.hasCity()) {
				continue;
			}
			double regionValue = 0;
			int rawRegionValue = 0;
			for (Connection conn : region.connections()) {
				regionValue += connectionWorthMap.getOrDefault(conn, 0);
			}
			rawRegionValue = (int) regionValue;
			regionValue /= MatchConstants.INSTABILITY_THRESHOLD - region.instability();

			if (regionValue < worstRegionValue) {
				worstRegionValue = regionValue;
				regionCandidateToDisrupt.clear();
				regionCandidateToDisrupt.add(region);
			} else if (regionValue == worstRegionValue) {
				regionCandidateToDisrupt.add(region);
			}
			if (regionValue != 0) {
				Print.debug(
						"Region " + region.id() + " has raw value: " + rawRegionValue + " and value: " + regionValue);
			}
		}
		if (regionCandidateToDisrupt.isEmpty()) {
			Print.debug("Nothing to disrupt, sorry mate");
		} else  if (regionCandidateToDisrupt.size() == 1) {
			Print.debug("Single region to disrupt, just do it");
			result = Action.disruptRegion(regionCandidateToDisrupt.get(0).id());
		}		else {
			Print.debug(regionCandidateToDisrupt.size()
					+ " regions candidate to disrupt, going to kill based on max balance of rails");

			Region regionToKill = null;
			double bestBalance = 100;
			if (worstRegionValue == 0) {
				bestBalance = 0;
			}
			for (Region region : regionCandidateToDisrupt) {

				double balance = 0;
				for (Rail rail : gs.rails().values()) {
					if (region.id() == gs.map().regionIdAt(rail.x(), rail.y())) {
						if (rail.owner == RailOwner.OPPONENT) {
							balance--;
						} else if (rail.owner == RailOwner.ME) {
							balance++;
						}
					}
				}

				// Print.debug("Region " + region.id() + " has a raw rail balance of: " +
				// balance);

				balance = balance / (MatchConstants.INSTABILITY_THRESHOLD - region.instability());
				// Print.debug("Region " + region.id() + " has a modulated rail balance of: " +
				// balance);

				if (balance <= bestBalance) {
					bestBalance = balance;
					regionToKill = region;
				}

			}

			if (regionToKill != null) {
				Print.debug("Going to kill " + bestBalance + " balanced rails from region " + regionToKill.id());
				result = Action.disruptRegion(regionToKill.id());
			} else {
				Print.debug("I guess there's a bug");
			}

		}
		Time.debugDuration("Disrupt action computation end");
		return result;
	}
}

record NAMOAPathsForCity(City city, Map<Integer, List<NAMOAPath>> pathsToTargets) {
}

/**
 * Extremely basic AI used as a placeholder. Always returns one dummy action at
 * (0,0) with ActionType.NONE so tests can assert non-empty output.
 */
class SimpleAI implements AI {

	public static int GET_TOP_PATHS_COUNT = 1;
	public static boolean BUILD_ONLY_IN_ONE_REGION_PER_TURN = false;
	public static boolean BUILD_USING_HEAT_MAP = false;

	Random r = new Random();

	public List<NAMOAPath> findSortedCheapestPaths(GameState gs,
			Map<Integer, NAMOAPathsForCity> possiblePathsMapFromCityMap) {
		List<NAMOAPath> allPaths = new ArrayList<>();
		for (City city : gs.map().citiesById()) {
			NAMOAPathsForCity namoaPathsForCity = possiblePathsMapFromCityMap.get(city.id());
			if (namoaPathsForCity == null) {
				continue;
			}
			Map<Integer, List<NAMOAPath>> possiblePathsMap = namoaPathsForCity.pathsToTargets();
			if (!city.desiredCityIds().isEmpty()) {
				for (Entry<Integer, List<NAMOAPath>> target : possiblePathsMap.entrySet()) {
					List<NAMOAPath> paths = possiblePathsMap.get(target.getKey());
					allPaths.addAll(paths);
				}
			}
		}

		List<NAMOAPath> sortedPaths = sortCheapestPaths(allPaths);
		return sortedPaths;
	}

	public List<NAMOAPath> sortCheapestPaths(List<NAMOAPath> paths) {
		paths.sort((p1, p2) -> Integer.compare(p1.buildCost(), p2.buildCost()));
		return paths;
	}

	List<Action> buildRailsAlongHeatMap(GameState gs, List<NAMOAPath> paths) {
		Set<Action> railActions = new TreeSet<>();
		int remainingBuildCapacity = MatchConstants.MAX_ACTIONS_PER_TURN;
		Set<Coord> builtCoords = new HashSet<>();
		Set<Integer> builtInRegion = new HashSet<>();

		HeatMap heatMap = new HeatMap();
		heatMap.build(paths);

		List<Map.Entry<Coord, Integer>> possibleBuildCoords = heatMap.sortedHeatMapEntries;

		// Try to build as many rails as possible along the heat map
		for (int i = 0; i < MatchConstants.MAX_ACTIONS_PER_TURN; i++) {
			builtInRegion.clear();

			// Build rails in regions we haven't built in this turn yet
			Set<Coord> coordsToRemove = new HashSet<>();
			for (Map.Entry<Coord, Integer> buildCoordEntry : possibleBuildCoords) {
				Coord buildCoord = buildCoordEntry.getKey();
				int regionId = gs.regionIdAt(buildCoord);
				if (BUILD_ONLY_IN_ONE_REGION_PER_TURN && builtInRegion.contains(regionId)) {
					Print.debug("Skipping rail at (" + buildCoord.x() + "," + buildCoord.y()
							+ ") as we already built in region " + regionId + " this turn");
					continue;
				}
				coordsToRemove.add(buildCoord);
				if (builtCoords.contains(buildCoord)) {
					Print.debug("Skipping rail at (" + buildCoord.x() + "," + buildCoord.y()
							+ ") as we already built there this turn");
					continue;
				}
				if (gs.map().buildCostAt(buildCoord.x(), buildCoord.y()) > remainingBuildCapacity) {
					Print.debug("Skipping rail at (" + buildCoord.x() + "," + buildCoord.y()
							+ ") as not enough remaining build capacity");
					continue;
				}
				railActions.add(Action.buildRail(buildCoord.x(), buildCoord.y()));
				remainingBuildCapacity -= gs.map().buildCostAt(buildCoord.x(), buildCoord.y());
				builtInRegion.add(regionId); // remember we've built in this region
				builtCoords.add(buildCoord); // remember we've built here

				Print.debug("Placing rail at (" + buildCoord.x() + "," + buildCoord.y() + ") in region " + regionId
						+ ", remaining build capacity: " + remainingBuildCapacity);

				if (remainingBuildCapacity <= 0) {
					Print.debug("No remaining build capacity, stopping rail placement");
					return railActions.stream().toList();
				}
			}
			possibleBuildCoords.removeAll(coordsToRemove);
			if (possibleBuildCoords.isEmpty()) {
				break;
			}
		}
		return railActions.stream().toList();
	}

	/**
	 * Builds rails along a path where rails don't already exist.
	 * Returns a list of PLACE_TRACKS actions for cells that need rails.
	 */
	public List<Action> buildRailsAlongPath(GameState gs, List<NAMOAPath> paths) {
		Set<Action> railActions = new TreeSet<>();
		int remainingBuildCapacity = MatchConstants.MAX_ACTIONS_PER_TURN;
		Set<Coord> builtCoords = new HashSet<>();
		Set<Integer> builtInRegion = new HashSet<>();

		for (NAMOAPath path : paths) {
			List<Coord> possibleBuildCoords = new ArrayList<>();

			for (Coord coord : path.path()) {
				// Skip cities (start and end points)
				if (!gs.canBuildAt(coord) || gs.rails().containsKey(coord)
						|| gs.map().regions()[gs.map().regionIdAt(coord)].instability() >= 2) {
					continue;
				}

				// Only place rail if there isn't one already
				if (gs.map().buildCostAt(coord.x(), coord.y()) <= remainingBuildCapacity
						&& !builtCoords.contains(coord)) {
					possibleBuildCoords.add(coord);
				}
			}

			if (possibleBuildCoords.isEmpty()) {
				Print.debug("No possible build coords along path from city " + path.from().id() + " to city "
						+ path.to().id());
				continue;
			}

			// Try to build as many rails as possible along the path
			for (int i = 0; i < MatchConstants.MAX_ACTIONS_PER_TURN; i++) {
				builtInRegion.clear();

				// Build rails in regions we haven't built in this turn yet
				Set<Coord> coordsToRemove = new HashSet<>();
				for (Coord buildCoord : possibleBuildCoords) {
					int regionId = gs.regionIdAt(buildCoord);
					if (BUILD_ONLY_IN_ONE_REGION_PER_TURN && builtInRegion.contains(regionId)) {
						Print.debug("Skipping rail at (" + buildCoord.x() + "," + buildCoord.y()
								+ ") as we already built in region " + regionId + " this turn" + ", connecting city "
								+ path.to().id());
						continue;
					}
					coordsToRemove.add(buildCoord);
					if (builtCoords.contains(buildCoord)) {
						Print.debug("Skipping rail at (" + buildCoord.x() + "," + buildCoord.y()
								+ ") as we already built there this turn" + ", connecting city "
								+ path.to().id());
						continue;
					}
					if (gs.map().buildCostAt(buildCoord.x(), buildCoord.y()) > remainingBuildCapacity) {
						Print.debug("Skipping rail at (" + buildCoord.x() + "," + buildCoord.y()
								+ ") as not enough remaining build capacity" + ", connecting city "
								+ path.to().id());
						continue;
					}
					railActions.add(Action.buildRail(buildCoord.x(), buildCoord.y()));
					remainingBuildCapacity -= gs.map().buildCostAt(buildCoord.x(), buildCoord.y());
					builtInRegion.add(regionId); // remember we've built in this region
					builtCoords.add(buildCoord); // remember we've built here

					Print.debug("Placing rail at (" + buildCoord.x() + "," + buildCoord.y() + ") in region " + regionId
							+ ", remaining build capacity: " + remainingBuildCapacity + ", connecting city "
							+ path.to().id());

					if (remainingBuildCapacity <= 0) {
						Print.debug("No remaining build capacity, stopping rail placement");
						return railActions.stream().toList();
					}
				}
				possibleBuildCoords.removeAll(coordsToRemove);
				if (possibleBuildCoords.isEmpty()) {
					break;
				}
			}
		}
		return railActions.stream().toList();
	}

	public List<NAMOAPath> filterPathsByBuildCost(List<NAMOAPath> paths, int maxBuildCost) {
		List<NAMOAPath> filtered = new ArrayList<>();
		for (NAMOAPath path : paths) {
			if (path.buildCost() <= maxBuildCost) {
				filtered.add(path);
			}
		}
		return filtered;
	}

	public List<NAMOAPath> getTopPaths(List<NAMOAPath> paths, int nbPaths) {
		return paths.subList(0, Math.min(nbPaths, paths.size()));
	}

	public NAMOAPathsForCity findNAMOAPathsForCity(City city, GameState gs) {
		if (city.desiredCityIds().isEmpty()) {
			return null;
		}

		final GameState fgs = gs;

		// I target only cities I don't have a connection to yet
		List<City> targetCities = new ArrayList<>();

		Print.debug(fgs.cachedConnections().stream().map(c -> c.fromId() + "-" + c.toId()).collect(
				java.util.stream.Collectors.joining(", ")) + " cached connections before NAMOA* for city " + city.id()
				+ " desires " + city.desiredCityIds().stream().map(String::valueOf)
						.collect(java.util.stream.Collectors.joining(", ")));
		for (int desiredCityId : city.desiredCityIds()) {
			if (!fgs.cachedConnections()
					.contains(MatchConstants.connectionLookup[city.id()][desiredCityId])) {
				targetCities.add(fgs.map().citiesById()[desiredCityId]);
			}
		}

		Long duration = Time.getRoundDuration();
		if (targetCities.isEmpty() || !Time.isTimeLeft(gs.round() == 1)) {
			if (targetCities.isEmpty()) {
				Print.debug(duration + "ms: Not computing NAMOA* paths from city " + city.id()
						+ " - all desired cities already connected (desired: "
						+ city.desiredCityIds().stream().map(String::valueOf)
								.collect(java.util.stream.Collectors.joining(","))
						+ ", cached connections: " + fgs.cachedConnections().size() + ")");
			} else {
				Print.debug(duration + "ms: Not computing NAMOA* paths from city " + city.id()
						+ " - time limit reached");
			}
			return null;
		}

		// I compute possible paths to those target cities
		// Print.debug(duration + "ms: Computing NAMOA* paths from city " + city.id() +
		// " to "
		// + targetCities.stream().map(c -> Integer.toString(c.id()))
		// .collect(java.util.stream.Collectors.joining(", ")));

		Map<Integer, List<NAMOAPath>> possiblePathsMap = NAMOAStar.findPaths(gs, city, targetCities);

		if (GET_TOP_PATHS_COUNT > 0) {
			// Print.debug(
			// "Filtering to keep only the top " + GET_TOP_PATHS_COUNT + " paths per target
			// city");
			// filter out similar paths to keep only the non-dominated ones
			for (Entry<Integer, List<NAMOAPath>> entry : possiblePathsMap.entrySet()) {
				Integer targetCityId = entry.getKey();
				// I take the first path as is
				List<NAMOAPath> possibleTopPaths = entry.getValue();
				int nbTopPaths = Math.min(GET_TOP_PATHS_COUNT, possibleTopPaths.size());
				possiblePathsMap.put(targetCityId, possibleTopPaths.subList(0, nbTopPaths));
			}
		}

		// I store them for later use
		return new NAMOAPathsForCity(city, possiblePathsMap);
	}

	class HeatMap {
		Map<Coord, Integer> heatMap;
		List<Map.Entry<Coord, Integer>> sortedHeatMapEntries;

		HeatMap() {
			heatMap = new HashMap<>();
		}

		void build(List<NAMOAPath> paths) {
			for (NAMOAPath path : paths) {
				for (Coord coord : path.path()) {
					heatMap.put(coord, heatMap.getOrDefault(coord, 0) + 1);
				}
			}
			sortedHeatMapEntries = new ArrayList<>(heatMap.entrySet());
			// Sort descending by heat value
			sortedHeatMapEntries.sort((e1, e2) -> Integer.compare(-e2.getValue(), -e1.getValue()));
		}
	}

	@Override
	public List<Action> compute(GameState gs) {
		List<Action> result = new ArrayList<Action>();

		Time.debugDuration("Starting SimpleAI compute");

		Action disruptAction = getDisruptAction(gs);
		if (disruptAction != null) {
			result.add(disruptAction);
			gs = gs.increaseInstability(disruptAction.id());
		}

		Time.debugDuration("Starting NAMOA");
		Map<Integer, NAMOAPathsForCity> namoaPathsForCityMap = new HashMap<>();

		for (City city : gs.map().citiesById()) {
			NAMOAPathsForCity namoaPathsForCity = findNAMOAPathsForCity(city, gs);
			if (namoaPathsForCity != null) {
				// I store them for later use
				namoaPathsForCityMap.put(city.id(), namoaPathsForCity);
			}
		}

		List<NAMOAPath> cheapestPaths = findSortedCheapestPaths(gs, namoaPathsForCityMap);
		if (cheapestPaths != null && !cheapestPaths.isEmpty()) {
			List<Action> railActions;
			if (BUILD_USING_HEAT_MAP) {
				railActions = buildRailsAlongHeatMap(gs, cheapestPaths);
			} else {
				railActions = buildRailsAlongPath(gs, cheapestPaths);
			}
			Player.nbBuild += railActions.size();
			Player.nbNoBuild += railActions.size() == 0 ? 1 : 0;
			result.addAll(railActions);
			Time.debugDuration("Finished building rails along heat map");
		}

		Time.debugDuration("Finished NAMOA");

		if (result.isEmpty()) {
			result.add(Action.waitAction());
		}

		Time.debugDuration("Finished compute");

		return result;
	}

}