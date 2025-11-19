import java.util.Random;
import java.util.Collections;

class SimulatedAnnealingAI extends AI {

	public static double initialTemperature = 0; // 100 for instance
	public static double coolingRate = 0; // 0.001 for instance
	public static double maxScore = 0; // 100 for instance
	private Random r = new Random();
	public Action[] bestActionsFromLastTurn;

	@Override
	public Action compute(GameState gs) {

		GameState currentGs = gs.copy();
		GameEngine.nbApplyAction = 0;

		double temperature = initialTemperature;
		int iterations = 0;
		int acceptance = 0;

		Action[] currentActions = generateRandomIndividual(currentGs);
		Action[] bestActions = currentActions;

		double currentScore = getActionListScore(currentActions, currentGs, false);
		double bestScore = currentScore;

		Action[] newActions;
		double newScore;

		Print.debug("First iteration: " + iterations + " nbApplyAction: " + Print.formatIntFixedLenght(6, GameEngine.nbApplyAction) + " bestScore: "
				+ Print.formatDoubleFixedLenghtAFterComma(3, 4, bestScore) + " temperature: " + Print.formatDoubleFixedLenghtAFterComma(3, 4, temperature));

		while (Time.isTimeLeft(gs.round() == 1)) {

			currentGs = gs.copy();

			newActions = getNeighbour(currentActions, currentGs);
			newScore = getActionListScore(newActions, currentGs, false);

			// Decide if we should accept the neighbour
			if (acceptanceProbability(currentScore, newScore, temperature) > Math.random()) {
				currentActions = newActions;
				currentScore = newScore;
				acceptance++;
			}

			// Keep track of the best solution found
			if (currentScore < bestScore) {
				bestActions = currentActions;
				bestScore = currentScore;
			}

			// Cool system
			temperature = getTemperature(temperature);
			iterations++;

			if (iterations % 1000 == 1) {
				Print.debug(
						"Full iterations: " + iterations + " nbApplyAction: " + GameEngine.nbApplyAction + " bestScore: " + Print.formatDoubleFixedLenghtAFterComma(3, 4, bestScore) + " temperature: "
								+ Print.formatDoubleFixedLenghtAFterComma(3, 4, temperature) + " AccRatio: " + Print.formatDoubleFixedLenghtAFterComma(3, 2, acceptance / (double) iterations * 100));
			}

		}

		Print.debug("Full iterations: " + iterations + " nbApplyAction: " + GameEngine.nbApplyAction + " bestScore: " + Print.formatDoubleFixedLenghtAFterComma(3, 4, bestScore) + " temperature: "
				+ Print.formatDoubleFixedLenghtAFterComma(3, 4, temperature) + " AccRatio: " + Print.formatDoubleFixedLenghtAFterComma(3, 2, acceptance / (double) iterations * 100));

		// Keep the best moves to restart from here next turn. Probably a good idea, but not necessarily always better than restarting from scratch
		if (bestScore < maxScore) {
			bestActionsFromLastTurn = bestActions;
		} else {
			bestActionsFromLastTurn = null;
		}

		return Collections.singletonList(bestActions[0]);
	}

	public static double acceptanceProbability(double score, double newScore, double temperature) {
		// If the new solution is better, accept it
		if (newScore < score) {
			return 1.0;
		}
		// If the new solution is worse, calculate an acceptance probability
		return Math.exp((score - newScore) / temperature);
	}

	private double getActionListScore(Action[] actions, GameState gs, boolean logTurns) {
		if (logTurns) {
			gs.print();
		}
		for (Action action : actions) {

			GameEngine.applyActionWithoutCopy(gs, action, null);
			if (logTurns) {
				gs.print();
			}
			if (gs.gameResult != GameResult.UNKNOWN) {
				break;
			}
		}
		return getGameStateScore(gs);
	}

	private static void debugActionList(Action[] actionList) {
		if (actionList != null) {
			String bestActions = "ActionList: ";
			for (Action action : actionList) {
				bestActions += action.message + Print.debugSep;
			}
			Print.debugForInput(bestActions);
		}
	}

	// TODO: implement generateRandomIndividual
	public Action[] generateRandomIndividual(GameState gs) {

		Action[] result = null;

		if (bestActionsFromLastTurn == null) {

			// TODO: generate a random solution

		} else {
			System.arraycopy(bestActionsFromLastTurn, 1, result, 0, bestActionsFromLastTurn.length - 1);
			result[result.length - 1] = null;
		}

		return result;
	}

	// TODO: implement getNeighbour
	public Action[] getNeighbour(Action[] currentActions, GameState gs) {

		Action[] result = null;
		return result;
	}

	// TODO: implement score
	public double getGameStateScore(GameState gs) {
		double score = 0;

		return score;
	}

	public double getTemperature(double currentTemperature) {
		return currentTemperature * (1 - coolingRate);
	}

	@Override
	public void printAIParameters() {
		debugActionList(bestActionsFromLastTurn);

	}

}