import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

// Generic action types placeholder for the skeleton
enum ActionType {
	NONE
}

interface Element {
	int x();

	int y();
}

class Grid {

	public final Element[][] grid;
	public final int width, height;

	// this hashset should compare solely the x() and y() to define if 2 objects are
	// equals and override with the new object
	// public final Map<ResourceType, Set<Element>> resources = new HashMap<>();

	public Grid(int width, int height) {
		this.width = width;
		this.height = height;
		grid = new Element[width][height];
	}

	public Grid copy() {
		Grid copy = new Grid(width, height);
		return copy;
	}

	public void setElement(Element element) {
		Element oldElement = grid[element.x()][element.y()];
		removeElement(oldElement);
		grid[element.x()][element.y()] = element;
	}

	public void removeElement(Element element) {
		if (element == null) {
			return;
		}
		removeElement(element.x(), element.y());
	}

	public void removeElement(int x, int y) {
		Element element = grid[x][y];
		if (element != null) {
			grid[x][y] = null;
		}
	}

	public Element getElement(int x, int y) {
		return grid[x][y];
	}

	public void print() {
		for (int y = 0; y < grid[0].length; y++) {
			for (int x = 0; x < grid.length; x++) {
				System.err.print(grid[x][y] + " ");
			}
			System.err.println();
		}
	}

}

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
	private static GameState predictedGameState;
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

		Time.startRoundTimer(); // Unless there is nothing to read in the scanner outside of the loop !
		// Memory.initMemory();
		MatchConstants.print();

	}

	static GameState initRound(Scanner in) {

		GameState result = null;

		if (previousGameState == null) {
			result = new GameState(1, new Grid(MatchConstants.width, MatchConstants.height));
		} else {
			result = new GameState(previousGameState.round() + 1,
					new Grid(MatchConstants.width, MatchConstants.height));
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
				Print.debug("Ran comparison between the input and the prediction and predicted:");
				predictedGameState.print();
				Print.debug("Stop the game");
			}

			stopGame = true;

		} else {
			if (isDebugOn) {
				Print.debug("Prediction ok !");
			}
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

// Minimal action representation (no domain specifics like resources/organs)
record Action(ActionType type, int x, int y) {

	@Override
	public String toString() {
		// Output kept deliberately simple to ease later parsing / extension
		return type + " " + x + " " + y;
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

// Core game state skeleton: only round count and grid
record GameState(int round, Grid grid) implements GameStateObject {
	public GameState copy() {
		return new GameState(round, grid.copy());
	}
}

interface AI {

	// Will compute an Action from a provided GameState which is going to stay
	// INTACT during the computation
	// This is the default entry point for an AI
	public default List<Action> computeIntact(GameState gs) {

		// Defensive: allow null GameState for tests or early skeleton usage
		if (gs == null) {
			gs = new GameState(1, new Grid(1, 1));
		}
		// Work on a copy to keep provided GameState intact
		GameState gsCopy = gs.copy();
		return compute(gsCopy);
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
		// Defensive: create a minimal GameState when null (tests currently pass null)
		if (gs == null) {
			gs = new GameState(1, new Grid(1, 1));
		}
		// Produce a single placeholder action so that tests expecting >0 actions pass
		return List.of(new Action(ActionType.NONE, 0, 0));
	}
}