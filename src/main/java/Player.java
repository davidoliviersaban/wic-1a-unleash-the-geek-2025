import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Queue;
import java.util.Random;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Scanner;
import java.util.stream.Collectors;

// Legacy embedded types removed. See new standalone records (Coord, TerrainCell, City, Region, Rail, Action, GameState, MapDefinition).

/**
 * Core entry point for the skeleton game implementation.
 * <p>
 * Purpose: fast iteration later. All domain-specific concepts (resources,
 * organs, proteins, etc.) have been stripped out. Only timing, grid handling
 * and a placeholder AI with a single dummy action remain.
 */
class Player {

	// Easy to find switches
	public static final boolean isDebugOn = true;
	public static final boolean isCompareFailureOn = false;
	public static int ME;
	public static int OPP;

	// Magic numbers

	// AIs
	private static AI ai = new SimpleAI();

	// Game constants, write them here once for all. The match constants however
	// should go in MatchConstants

	// Game variables
	private static GameState previousGameState;
	private static GameState predictedGameState; // placeholder for future prediction feature
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
		TerrainCell[][] terrain = new TerrainCell[width][height];
		Map<Coord, Integer> coordToRegionId = new HashMap<>();
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

				terrain[x][y] = new TerrainCell(x, y, terrainType, null);
				coordToRegionId.put(MatchConstants.coord(x, y), regionId);
				regionIds.add(regionId);
			}
		}

		// Parse cities (towns)
		int townCount = in.nextInt();
		Map<Integer, City> citiesById = new HashMap<>();

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

			Integer regionId = coordToRegionId.get(MatchConstants.coord(townX, townY));
			City city = new City(townId, townX, townY, regionId != null ? regionId : -1, desiredCityIds);
			citiesById.put(townId, city);

			// Embed city in terrain

			MatchConstants.cityList.add(city);
			terrain[townX][townY] = new TerrainCell(townX, townY, terrain[townX][townY].type(), city);
		}

		MatchConstants.cityCount = townCount;
		MatchConstants.regionsCount = regionIds.size();

		// Create initial MapDefinition
		MapDefinition mapDef = new MapDefinition(width, height, terrain, citiesById, coordToRegionId, regionIds.size());

		// Initialize regions array
		Region[] regions = new Region[regionIds.size()];
		for (int id : regionIds) {
			if (id >= 0 && id < regions.length) {
				regions[id] = new Region(id, 0);
			}
		}

		// Create initial game state
		previousGameState = new GameState(1, mapDef, new HashMap<>(), regions, 0, 0, null);

		Time.startRoundTimer();
		MatchConstants.print();
	}

	static GameState initRound(Scanner in) {

		GameState result = null;

		if (previousGameState == null) {
			// Initial blank map definition via GameState factory
			result = GameState.createInitial(MatchConstants.width, MatchConstants.height);
		} else {
			result = previousGameState.nextRound();
		}

		// Read scores
		int myScore = in.nextInt();
		int foeScore = in.nextInt();
		Time.startRoundTimer();

		// Update game state from previous round
		result = previousGameState.withScores(myScore, foeScore);

		// Parse grid state
		Map<Coord, Rail> rails = new HashMap<>();
		Region[] regions = result.regions().clone();
		Set<Connection> connectionsSet = new TreeSet<>();

		for (int y = 0; y < MatchConstants.height; y++) {
			for (int x = 0; x < MatchConstants.width; x++) {
				int tracksOwner = in.nextInt();
				int instability = in.nextInt(); // region inked (destroyed) when this >= 3
				boolean inked = in.nextInt() != 0; // true if region is destroyed
				String partOfActiveConnectionStr = in.next(); // x or town connections like 0-1,1-2

				// Update rails
				if (tracksOwner >= 0) {
					RailOwner owner = RailOwner.NONE;
					if (tracksOwner == ME) {
						owner = RailOwner.ME;
					} else if (tracksOwner == OPP) {
						owner = RailOwner.OPPONENT;
					} else if (tracksOwner == -1) {
						owner = RailOwner.NONE;
					} else {
						owner = RailOwner.CONTESTED;
					}

					if (owner != RailOwner.NONE) {
						Rail rail = new Rail(x, y, owner);
						rail.partOfActiveConnections = new ArrayList<>();
						if (!partOfActiveConnectionStr.equals("x")) {
							String[] connections = partOfActiveConnectionStr.split(",");
							for (String conn : connections) {
								String[] ids = conn.split("-");
								Connection connection = new Connection(Integer.parseInt(ids[0]),
										Integer.parseInt(ids[1]));
								rail.partOfActiveConnections.add(connection);
								connectionsSet.add(connection);
							}
						}
						rails.put(MatchConstants.coord(x, y), rail);
					}
				}

				// Update region instability
				Integer regionId = result.map().coordToRegionId().get(MatchConstants.coord(x, y));
				if (regionId != null && regionId >= 0 && regionId < regions.length) {
					regions[regionId] = new Region(regionId, instability);
				}
			}
		}

		// Create updated game state
		result = new GameState(result.round(), result.map(), rails, regions, myScore, foeScore, connectionsSet);

		if (isDebugOn) {
			// result.print();
		}

		if (previousGameState != null) {
			compareInputAgainstPrediction(result);
		}

		return result;

	}

	// Runs a comparison between what CG gives us, and what we had predicted. Will
	// stop the game if any difference is found, in order to highlight the need of a
	// new test
	private static void compareInputAgainstPrediction(GameState gameStateFromInput) {
		if (isCompareFailureOn && predictedGameState != null && !predictedGameState.equals(gameStateFromInput)) {
			if (isDebugOn) {
				Print.debug("Prediction mismatch detected. (No pretty print available)");
				Print.debug("Stop the game");
			}
			stopGame = true;
		} else if (isDebugOn) {
			Print.debug("Prediction ok !");
		}

	}

	private static void finalizeRound(List<Action> actions, GameState gs) {

		if (!stopGame) {

			// List<Action> opActions = null;
			// if (opAi != null) {
			// opActions = opAi.computeIntact(gs);
			// }

			if (isDebugOn) {

				Print.debug("My action:");
				// Print.debugForInput(actions.toString());

				// if (opActions != null) {
				// Print.debug("Op action:");
				// Print.debugForInput("" + opActions);
				// }

			}

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
			String output = "";
			output = actions.stream().map(Action::toString).collect(Collectors.joining(";"));
			System.out.println(output);
		}

	}

}

// Stores stuff which is not going to change for the whole match, but could
// change from one match to another
class MatchConstants {

	public static int cityCount;
	public static int regionsCount;
	public static final int INSTABILITY_THRESHOLD = 3;
	public static int height;
	public static int width;
	private static Coord[][] coords;
	public static List<City> cityList = new ArrayList<City>();

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
	PLAIN, RIVER, MOUNTAIN, POI
}

record TerrainCell(int x, int y, TerrainType type, City city) {

	TerrainCell(int x, int y, TerrainType type) {
		this(x, y, type, null);
	}

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

record Region(int id, int instability) {
	boolean isInstable() {
		return instability >= MatchConstants.INSTABILITY_THRESHOLD;
	}

	Region increaseInstability() {
		return new Region(id, instability + 1);
	}

	boolean containsACity() {
		for (City city : MatchConstants.cityList) {
			if (city.regionId() == id) {
				return true;
			}
		}
		return false;
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

record MapDefinition(int width, int height, TerrainCell[][] terrain, Map<Integer, City> citiesById,
		Map<Coord, Integer> coordToRegionId, int regionCount) {

	TerrainCell cell(int x, int y) {
		return terrain[x][y];
	}

	boolean hasCity(int x, int y) {
		return terrain[x][y].hasCity();
	}

	City getCityAt(int x, int y) {
		return terrain[x][y].city();
	}
}

record Action(ActionType type, Coord coord1, Coord coord2) {
	static Action buildRail(int x, int y) {
		return new Action(ActionType.PLACE_TRACKS, MatchConstants.coord(x, y), null);
	}

	static Action autoPlace(int x1, int y1, int x2, int y2) {
		return new Action(ActionType.AUTOPLACE, MatchConstants.coord(x1, y1), MatchConstants.coord(x2, y2));
	}

	static Action disrupt(int x, int y) {
		return new Action(ActionType.DISRUPT, MatchConstants.coord(x, y), null);
	}

	static Action waitAction() {
		return new Action(ActionType.WAIT, null, null);
	}

	@Override
	public String toString() {
		return switch (type) {
			case PLACE_TRACKS -> ActionType.PLACE_TRACKS + " " + coord1.x() + " " + coord1.y();
			case AUTOPLACE ->
				ActionType.AUTOPLACE + " " + coord1.x() + " " + coord1.y() + " " + coord2.x() + " " + coord2.y();
			case DISRUPT -> ActionType.DISRUPT + " " + coord1.x() + " " + coord1.y();
			case MESSAGE -> ActionType.MESSAGE + " insert a message here";
			case WAIT -> "WAIT";
		};
	}
}

record GameState(int round, MapDefinition map, Map<Coord, Rail> rails, Region[] regions, int myScore,
		int opponentScore, Set<Connection> cachedConnections) {
	GameState nextRound() {
		return new GameState(round + 1, map, rails, regions, myScore, opponentScore, cachedConnections);
	}

	GameState withRails(List<Coord> coords, RailOwner owner) {
		Map<Coord, Rail> newRails = new HashMap<>(rails);
		for (Coord c : coords) {
			Rail existing = newRails.get(c);
			Rail newRail = existing == null ? new Rail(c.x(), c.y(), owner) : resolveConflict(existing, owner);
			newRails.put(c, newRail);
		}
		return new GameState(round, map, newRails, regions, myScore, opponentScore, cachedConnections);
	}

	private Rail resolveConflict(Rail existing, RailOwner incoming) {
		if (existing.owner() == incoming)
			return existing;
		return new Rail(existing.x, existing.y, RailOwner.CONTESTED);
	}

	Map<Coord, Rail> railsInRegionAsMap(int regionId) {
		Map<Coord, Rail> filtered = new HashMap<>();
		for (var e : rails.entrySet()) {
			Integer rid = map.coordToRegionId().get(e.getKey());
			// int rid = map.terrain()[e.getKey().x()][e.getKey().y()].regionId();
			if (rid == -1 || rid != regionId)
				filtered.put(e.getKey(), e.getValue());
		}
		return filtered;
	}

	boolean canBuildAt(Coord c) {
		if (!c.isInside(map.width(), map.height()))
			return false;
		Integer regionId = map.coordToRegionId().get(c);
		// int regionId = map.terrain()[c.x()][c.y()].regionId();
		if (regionId != null && regionId != -1 && regions.length > regionId && regions[regionId].isInstable())
			return false;
		TerrainCell cell = map.terrain()[c.x()][c.y()];
		return !cell.hasCity();
	}

	GameState increaseInstability(int regionId) {
		Region[] newRegions = regions.clone();
		if (regionId >= 0 && regionId < newRegions.length) {
			newRegions[regionId] = newRegions[regionId].increaseInstability();
			if (newRegions[regionId].isInstable()) {
				Map<Coord, Rail> filtered = railsInRegionAsMap(regionId);
				return new GameState(round, map, filtered, newRegions, myScore, opponentScore, cachedConnections);
			}
		}
		return new GameState(round, map, rails, newRegions, myScore, opponentScore, cachedConnections);
	}

	GameState withScores(int my, int opp) {
		return new GameState(round, map, rails, regions, my, opp, cachedConnections);
	}

	GameState withCachedConnections(Set<Connection> connections) {
		return new GameState(round, map, rails, regions, myScore, opponentScore, connections);
	}

	static GameState createInitial(int width, int height) {
		TerrainCell[][] terrain = new TerrainCell[width][height];
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				terrain[x][y] = new TerrainCell(x, y, TerrainType.PLAIN); // Default to PLAIN
			}
		}
		MapDefinition mapDef = new MapDefinition(width, height, terrain, Map.of(), Map.of(), 0);
		return new GameState(1, mapDef, Map.of(), new Region[0], 0, 0, null);
	}
}

class GameEngine {
	static GameState applyActions(GameState state, List<Action> myActions, List<Action> oppActions) {
		GameState result = state;
		List<Coord> myCoords = buildCoords(myActions);
		List<Coord> oppCoords = buildCoords(oppActions);
		if (myCoords != null && oppCoords != null && !Collections.disjoint(myCoords, oppCoords)) {
			List<Coord> contested = new ArrayList<>(myCoords);
			contested.retainAll(oppCoords);
			result = result.withRails(contested, RailOwner.CONTESTED);
			myCoords.removeAll(contested);
			oppCoords.removeAll(contested);
			if (myCoords.isEmpty() == false)
				result = result.withRails(myCoords, RailOwner.ME);
			if (oppCoords.isEmpty() == false)
				result = result.withRails(oppCoords, RailOwner.OPPONENT);
		} else {
			if (myCoords != null)
				result = result.withRails(myCoords, RailOwner.ME);
			if (oppCoords != null)
				result = result.withRails(oppCoords, RailOwner.OPPONENT);
		}
		return result.nextRound();
	}

	private static List<Coord> buildCoords(List<Action> actions) {
		if (actions == null)
			return null;
		return actions.stream().filter(a -> a.type() == ActionType.PLACE_TRACKS).map(Action::coord1).toList();
	}
}

interface GameStateObject {

	public default void print() {
		Print.debugForInput(toString());
	}

}

enum GameResult {
	UNKNOWN, // Game not yet finished
	WON, // Game finished and won by us :)
	LOST, // Game finished and lost :(
	TIE // Game finished and tied
}

// Pathfinding using BFS for shortest rail paths

record CityConnection(City from, City to, List<Coord> path, int distance) {
}

class RailPathfinder {

	/**
	 * Returns a list of city pairs that are connected by rails with their shortest
	 * paths. Uses BFS to find shortest paths on the rail network.
	 */
	static List<CityConnection> findConnectedCityPairs(GameState gs) {
		List<CityConnection> connections = new ArrayList<>();
		Map<Integer, City> cities = gs.map().citiesById();

		// For each pair of cities, check if they're connected
		for (City from : cities.values()) {
			for (City to : cities.values()) {
				if (from.id() >= to.id())
					continue; // Avoid duplicates and self-connections

				List<Coord> path = findShortestRailPath(gs, from, to);
				if (path != null && !path.isEmpty()) {
					connections.add(new CityConnection(from, to, path, path.size()));
				}
			}
		}

		return connections;
	}

	/**
	 * Finds the shortest path between two cities using only rails (BFS). Returns
	 * null if no path exists.
	 */
	static List<Coord> findShortestRailPath(GameState gs, City from, City to) {
		Coord start = MatchConstants.coord(from.x(), from.y());
		Coord goal = MatchConstants.coord(to.x(), to.y());

		if (start.equals(goal))
			return List.of(start);

		Map<Coord, Coord> cameFrom = new HashMap<>();
		Queue<Coord> queue = new LinkedList<>();
		Set<Coord> visited = new HashSet<>();

		queue.add(start);
		visited.add(start);
		cameFrom.put(start, null);

		while (!queue.isEmpty()) {
			Coord current = queue.poll();

			if (current.equals(goal)) {
				return reconstructPath(cameFrom, current);
			}

			// Check all 4 adjacent cells
			for (Coord neighbor : getAdjacentRailCoords(gs, current)) {
				if (!visited.contains(neighbor)) {
					visited.add(neighbor);
					cameFrom.put(neighbor, current);
					queue.add(neighbor);
				}
			}
		}

		return null; // No path found
	}

	private static List<Coord> getAdjacentRailCoords(GameState gs, Coord coord) {
		List<Coord> adjacent = new ArrayList<>();
		// NORTH, EAST, SOUTH, WEST
		int[][] directions = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };

		for (int[] dir : directions) {
			int nx = coord.x() + dir[0];
			int ny = coord.y() + dir[1];

			if (MatchConstants.isValid(nx, ny)) {
				Coord neighbor = MatchConstants.coord(nx, ny);
				// Check if there's a rail at this position (or it's the destination city)
				if (gs.rails().containsKey(neighbor) || gs.map().hasCity(nx, ny)) {
					adjacent.add(neighbor);
				}
			}
		}

		return adjacent;
	}

	private static List<Coord> reconstructPath(Map<Coord, Coord> cameFrom, Coord current) {
		List<Coord> path = new ArrayList<>();
		while (current != null) {
			path.add(0, current);
			current = cameFrom.get(current);
		}
		return path;
	}
}

// NAMOA* (Non-dominated Archive Multi-Objective A*) pathfinding

record PathCost(int distance, int buildCost) implements Comparable<PathCost> {
	PathCost add(int distInc, int costInc) {
		return new PathCost(distance + distInc, buildCost + costInc);
	}

	boolean dominates(PathCost other) {
		// Simplify the dominance check
		// return this.buildCost < other.buildCost;
		return this.distance <= other.distance && this.buildCost <= other.buildCost
				&& (this.distance < other.distance || this.buildCost < other.buildCost);
	}

	@Override
	public int compareTo(PathCost other) {
		int sumThis = this.distance + this.buildCost;
		int sumOther = other.distance + other.buildCost;
		return Integer.compare(sumThis, sumOther);
	}
}

record NAMOANode(Coord coord, PathCost cost, PathCost heuristic, NAMOANode parent) implements Comparable<NAMOANode> {
	PathCost totalCost() {
		return new PathCost(cost.distance() + heuristic.distance(), cost.buildCost() + heuristic.buildCost());
	}

	@Override
	public int compareTo(NAMOANode other) {
		return this.totalCost().compareTo(other.totalCost());
	}
}

class NAMOAStar {

	/**
	 * Finds non-dominated paths from a start city to multiple target cities using
	 * * NAMOA*.
	 * Returns a map from target city ID to a list of non-dominated paths.
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
		PathCost initialCost = new PathCost(0, 0);
		PathCost initialHeuristic = computeHeuristic(gs, startCoord, targets);
		NAMOANode startNode = new NAMOANode(startCoord, initialCost, initialHeuristic, null);
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
			City reachedCity = gs.map().getCityAt(current.coord().x(), current.coord().y());
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

				PathCost newCost = current.cost().add(edgeDistance, edgeCost);

				// Check if dominated by archive at neighbor
				List<PathCost> neighborArchive = archive.get(neighbor);
				if (!isDominated(newCost, neighborArchive)) {
					PathCost newHeuristic = computeHeuristic(gs, neighbor, targets);
					NAMOANode newNode = new NAMOANode(neighbor, newCost, newHeuristic, current);
					open.add(newNode);

					// Update archive with size limit
					addToArchiveLimited(archive, neighbor, newCost, 3); // Max 3 non-dominated paths per cell
				}
			}
		}

		return results;
	}

	private static PathCost computeHeuristic(GameState gs, Coord from, List<City> targets) {
		int minDistance = Integer.MAX_VALUE;
		int minCost = 0;

		for (City target : targets) {
			int dist = Math.abs(from.x() - target.x()) + Math.abs(from.y() - target.y());
			if (dist < minDistance) {
				minDistance = dist;
				// Estimate cost (assuming average terrain)
				minCost = dist;
			}
		}

		return new PathCost(minDistance, minCost);
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

	private static void addToArchive(Map<Coord, List<PathCost>> archive, Coord coord, PathCost newCost) {
		addToArchiveLimited(archive, coord, newCost, Integer.MAX_VALUE);
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
				if (gs.canBuildAt(neighbor) || gs.rails().containsKey(neighbor) || gs.map().hasCity(nx, ny)) {
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
		if (gs.map().hasCity(coord.x(), coord.y())) {
			return 0;
		}
		// Otherwise, terrain build cost
		TerrainCell cell = gs.map().cell(coord.x(), coord.y());
		return cell.buildCost();
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

// GameState moved to dedicated file GameState.java

// GameEngine moved to its own file

class CityPair {
	City origin;
	City destination;

	public CityPair(City origin, City destination) {
		super();
		this.origin = origin;
		this.destination = destination;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Objects.hash(destination, origin);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		CityPair other = (CityPair) obj;
		return Objects.equals(destination, other.destination) && Objects.equals(origin, other.origin);
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

		Map<CityPair, Integer> cityPairs = new HashMap<CityPair, Integer>();

		// Builds the map of city pairs
		for (City origin : gs.map().citiesById().values()) {
			for (Integer destinationId : origin.desiredCityIds()) {
				City destination = gs.map().citiesById().get(destinationId);
				cityPairs.put(new CityPair(origin, destination), null);
			}

		}

		// add rails being part of active connections to the city pairs,
		Collection<Rail> rails = gs.rails().values();
		for (Rail rail : rails) {
			for (Connection connection : rail.partOfActiveConnections) {
				City origin = gs.map().citiesById().get(connection.fromId());
				City destination = gs.map().citiesById().get(connection.toId());
				CityPair cityPair = new CityPair(origin, destination);

				if (cityPairs.get(cityPair) == null) {
					cityPairs.put(cityPair, 0);
				}
				if (rail.owner == RailOwner.ME) {
					cityPairs.put(cityPair, cityPairs.get(cityPair) + 1);
				} else if (rail.owner == RailOwner.OPPONENT) {
					cityPairs.put(cityPair, cityPairs.get(cityPair) - 1);
				}

			}
		}

		CityPair worstCityPair = null;
		int worstConnection = 100000;
		for (CityPair cityPair : cityPairs.keySet()) {
			Print.debug("City pair from " + cityPair.origin.id() + " to " + cityPair.destination.id() + " is worth: "
					+ cityPairs.get(cityPair));
			if (cityPairs.get(cityPair) != null && cityPairs.get(cityPair) < worstConnection) {
				worstConnection = cityPairs.get(cityPair);
				worstCityPair = cityPair;
			}
		}

		if (worstCityPair != null && worstConnection < 0) {
			// Ink the first rail being part of the path and not belonging to a region with
			// a city
			for (Rail rail : rails) {

				if (rail.owner == RailOwner.OPPONENT) {

					for (Connection connection : rail.partOfActiveConnections) {
						City origin = gs.map().citiesById().get(connection.fromId());
						City destination = gs.map().citiesById().get(connection.toId());
						CityPair cityPair = new CityPair(origin, destination);

						if (cityPair.equals(worstCityPair)) {

							int regionId = gs.map().coordToRegionId().get(MatchConstants.coord(rail.x, rail.y));
							Region region = gs.regions()[regionId];
							if (!region.containsACity()) {
								result = Action.disrupt(rail.x, rail.y);
							}
						}

					}

				}

			}

		}
		return result;
	}
}

/**
 * Extremely basic AI used as a placeholder. Always returns one dummy action at
 * (0,0) with ActionType.NONE so tests can assert non-empty output.
 */
class StupidAI implements AI {

	Random r = new Random();

	@Override
	public List<Action> compute(GameState gs) {
		List<Action> result = new ArrayList<Action>();

		if (!gs.map().citiesById().isEmpty()) {
			City randomCity = gs.map().citiesById().get(r.nextInt(gs.map().citiesById().size()));
			if (randomCity != null && !randomCity.desiredCityIds().isEmpty()) {
				City randomDesired = gs.map().citiesById()
						.get(randomCity.desiredCityIds().get(r.nextInt(randomCity.desiredCityIds().size())));
				if (randomDesired != null) {
					Action randomAutoPlace = Action.autoPlace(randomCity.x(), randomCity.y(), randomDesired.x(),
							randomDesired.y());
					result.add(randomAutoPlace);
				}
			}
		}

		Action disruptAction = getDisruptAction(gs);
		if (disruptAction != null) {
			result.add(disruptAction);
		}

		if (result.isEmpty()) {
			result.add(Action.waitAction());
		}

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

	Random r = new Random();

	public NAMOAPath findCheapestPath(GameState gs, Map<Integer, NAMOAPathsForCity> possiblePathsMapFromCityMap) {
		NAMOAPath cheapestPath = null;
		for (City city : gs.map().citiesById().values()) {
			NAMOAPathsForCity namoaPathsForCity = possiblePathsMapFromCityMap.get(city.id());
			if (namoaPathsForCity == null) {
				continue;
			}
			Map<Integer, List<NAMOAPath>> possiblePathsMap = namoaPathsForCity.pathsToTargets();
			if (!city.desiredCityIds().isEmpty()) {
				for (Entry<Integer, List<NAMOAPath>> target : possiblePathsMap.entrySet()) {
					List<NAMOAPath> path = possiblePathsMap.get(target.getKey());
					for (NAMOAPath p : path) {
						if (cheapestPath == null || p.buildCost() < cheapestPath.buildCost()) {
							cheapestPath = p;
						}
					}
				}
			}
		}
		return cheapestPath;
	}

	@Override
	public List<Action> compute(GameState gs) {
		List<Action> result = new ArrayList<Action>();

		Map<Integer, NAMOAPathsForCity> namoaPathsForCityMap = new HashMap<>();

		for (City city : gs.map().citiesById().values()) {
			if (!city.desiredCityIds().isEmpty()) {
				// I target only cities I don't have a connection to yet
				List<City> targetCities = city.desiredCityIds().stream()
						.filter(id -> !gs.cachedConnections().contains(new Connection(city.id(), id)))
						.map(id -> gs.map().citiesById().get(id))
						.toList();

				// I compute possible paths to those target cities
				Long duration = Time.getRoundDuration();
				Print.debug(duration + "ms: Computing NAMOA* paths from city " + city.id() + " to "
						+ targetCities.stream().map(c -> Integer.toString(c.id()))
								.collect(java.util.stream.Collectors.joining(", ")));
				Map<Integer, List<NAMOAPath>> possiblePathsMap = NAMOAStar.findPaths(gs, city, targetCities);

				// I store them for later use
				namoaPathsForCityMap.put(city.id(), new NAMOAPathsForCity(city, possiblePathsMap));
			}
		}
		NAMOAPath cheapestPath = findCheapestPath(gs, namoaPathsForCityMap);
		if (cheapestPath != null) {
			Coord start = MatchConstants.coord(cheapestPath.from().x(), cheapestPath.from().y());
			Coord end = MatchConstants.coord(cheapestPath.to().x(), cheapestPath.to().y());
			Action autoPlaceAction = Action.autoPlace(start.x(), start.y(), end.x(), end.y());
			result.add(autoPlaceAction);
		}

		Action disruptAction = getDisruptAction(gs);
		if (disruptAction != null) {
			result.add(disruptAction);
		}

		if (result.isEmpty()) {
			result.add(Action.waitAction());
		}

		return result;
	}

}