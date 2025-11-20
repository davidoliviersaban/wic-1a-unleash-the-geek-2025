import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
	public static final int ME = 1;
	public static final int OPP = 0;

	// Magic numbers

	// AIs
	private static AI ai = new StupidAI();

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

		int width = in.nextInt(); // columns in the game grid
		int height = in.nextInt(); // rows in the game grid
		MatchConstants.height = height;
		MatchConstants.width = width;
		MatchConstants.regionsCount = 0; // will be set once regions parsed
		MatchConstants.cityCount = 0; // will be set once cities parsed

		Time.startRoundTimer(); // Unless there is nothing to read in the scanner outside of the loop !
		// Memory.initMemory();
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

		int entityCount = in.nextInt();
		Time.startRoundTimer();
		for (int i = 0; i < entityCount; i++) {

		}

		if (previousGameState != null) {
			// do something with the previous game state
		}

		if (isDebugOn) {
			// result.print();
		}

		compareInputAgainstPrediction(result);

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
			output = actions.stream().map(Action::toString).collect(Collectors.joining("\n"));
			System.out.println(output);
		}

	}

}

// Stores stuff which is not going to change for the whole match, but could
// change from one match to another
class MatchConstants {

	public static int cityCount;
	public static int regionsCount;
	public static final int INSTABILITY_THRESHOLD = 4;
	public static int height;
	public static int width;

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
	BUILD_RAIL, WAIT
}

record Coord(int x, int y) {
	boolean isInside(int width, int height) {
		return x >= 0 && x < width && y >= 0 && y < height;
	}
}

enum TerrainType {
	PLAIN, RIVER, MOUNTAIN
}

record TerrainCell(int x, int y, TerrainType type, City city, POI poi) {
	TerrainCell(int x, int y, TerrainType type) {
		this(x, y, type, null, null);
	}

	int buildCost() {
		return switch (type) {
			case PLAIN -> 1;
			case RIVER -> 2;
			case MOUNTAIN -> 3;
		};
	}

	boolean hasCity() {
		return city != null;
	}

	boolean hasPOI() {
		return poi != null;
	}
}

record City(int id, int x, int y, int regionId, List<Integer> desiredCityIds) {
	boolean desires(int otherCityId) {
		return desiredCityIds != null && desiredCityIds.contains(otherCityId);
	}
}

record POI(int id, int x, int y, int regionId) {
}

record Region(int id, int instability) {
	boolean isInstable(int threshold) {
		return instability >= threshold;
	}

	Region increaseInstability() {
		return new Region(id, instability + 1);
	}
}

enum RailOwner {
	ME, OPPONENT, CONTESTED, NONE;

	boolean isOwned() {
		return this == ME || this == OPPONENT;
	}
}

record Rail(int x, int y, RailOwner owner) {
	boolean isContested() {
		return owner == RailOwner.CONTESTED;
	}
}

record MapDefinition(
		int width,
		int height,
		TerrainCell[][] terrain,
		Map<Integer, City> citiesById,
		Map<Coord, Integer> coordToRegionId,
		int regionCount) {
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

record Action(ActionType type, Coord coord) {
	static Action buildRail(int x, int y) {
		return new Action(ActionType.BUILD_RAIL, new Coord(x, y));
	}

	static Action waitAction() {
		return new Action(ActionType.WAIT, null);
	}

	@Override
	public String toString() {
		return switch (type) {
			case BUILD_RAIL -> coord.x() + " " + coord.y();
			case WAIT -> "WAIT";
		};
	}
}

record GameState(
		int round,
		MapDefinition map,
		Map<Coord, Rail> rails,
		Region[] regions,
		int myScore,
		int opponentScore) {
	GameState nextRound() {
		return new GameState(round + 1, map, rails, regions, myScore, opponentScore);
	}

	GameState withRails(List<Coord> coords, RailOwner owner) {
		Map<Coord, Rail> newRails = new HashMap<>(rails);
		for (Coord c : coords) {
			Rail existing = newRails.get(c);
			Rail newRail = existing == null ? new Rail(c.x(), c.y(), owner) : resolveConflict(existing, owner);
			newRails.put(c, newRail);
		}
		return new GameState(round, map, newRails, regions, myScore, opponentScore);
	}

	private Rail resolveConflict(Rail existing, RailOwner incoming) {
		if (existing.owner() == incoming)
			return existing;
		return new Rail(existing.x(), existing.y(), RailOwner.CONTESTED);
	}

	boolean canBuildAt(Coord c) {
		if (!c.isInside(map.width(), map.height()))
			return false;
		Integer regionId = map.coordToRegionId().get(c);
		if (regionId != null && regions.length > regionId
				&& regions[regionId].isInstable(MatchConstants.INSTABILITY_THRESHOLD))
			return false;
		TerrainCell cell = map.terrain()[c.x()][c.y()];
		return !cell.hasCity() && !cell.hasPOI();
	}

	GameState increaseInstability(int regionId) {
		Region[] newRegions = regions.clone();
		if (regionId >= 0 && regionId < newRegions.length) {
			newRegions[regionId] = newRegions[regionId].increaseInstability();
			if (newRegions[regionId].isInstable(MatchConstants.INSTABILITY_THRESHOLD)) {
				Map<Coord, Rail> filtered = new HashMap<>();
				for (var e : rails.entrySet()) {
					Integer rid = map.coordToRegionId().get(e.getKey());
					if (rid == null || rid != regionId)
						filtered.put(e.getKey(), e.getValue());
				}
				return new GameState(round, map, filtered, newRegions, myScore, opponentScore);
			}
		}
		return new GameState(round, map, rails, newRegions, myScore, opponentScore);
	}

	GameState withScores(int my, int opp) {
		return new GameState(round, map, rails, regions, my, opp);
	}

	static GameState createInitial(int width, int height) {
		TerrainCell[][] terrain = new TerrainCell[width][height];
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				terrain[x][y] = new TerrainCell(x, y, TerrainType.PLAIN, null, null); // Default to PLAIN
			}
		}
		MapDefinition mapDef = new MapDefinition(width, height, terrain, Map.of(), Map.of(), 0);
		return new GameState(1, mapDef, Map.of(), new Region[0], 0, 0);
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
		return actions.stream().filter(a -> a.type() == ActionType.BUILD_RAIL).map(Action::coord).toList();
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

// Pathfinding (NAMOA*) removed during refactor; will be ported later to new
// model.

// GameState moved to dedicated file GameState.java

// GameEngine moved to its own file

interface AI {

	// Will compute an Action from a provided GameState which is going to stay
	// INTACT during the computation
	// This is the default entry point for an AI
	public default List<Action> computeIntact(GameState gs) {
		// For new immutable GameState we can safely pass it directly.
		if (gs == null) {
			gs = GameState.createInitial(MatchConstants.width > 0 ? MatchConstants.width : 26,
					MatchConstants.height > 0 ? MatchConstants.height : 17);
		}
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
}

/**
 * Extremely basic AI used as a placeholder. Always returns one dummy action at
 * (0,0) with ActionType.NONE so tests can assert non-empty output.
 */
class StupidAI implements AI {
	@Override
	public List<Action> compute(GameState gs) {
		if (gs == null) {
			gs = GameState.createInitial(MatchConstants.width > 0 ? MatchConstants.width : 26,
					MatchConstants.height > 0 ? MatchConstants.height : 17);
		}
		// Return a WAIT placeholder action
		return List.of(Action.waitAction());
	}
}