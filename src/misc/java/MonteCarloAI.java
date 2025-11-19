import java.util.Random;
import java.util.Collections;

class MonteCarloAI extends AI {

	private SolutionEvaluation eval;
	private AI opAI;
	private int depth;
	private Random r;

	public MonteCarloAI(SolutionEvaluation eval, AI opAI, int depth) {
		super();
		this.eval = eval;
		this.opAI = opAI;
		this.depth = depth;
		this.r = new Random();
	}

	@Override
	public List<Action> compute(GameState gs) {

		GameEngine.nbApplyAction = 0;
		int nbFullIterations = 0;

		Solution currentSolution;
		Solution bestSolution = null;

		double bestScore = -eval.maxScore;

		if (Player.isDebugOn) {
			Time.debugDuration("Before AI");
		}

		while (Time.isTimeLeft(gs.round() == 1)) {

			currentSolution = buildAndEvaluateRandomSolution(gs, opAI, eval);

			if (currentSolution.finalScore > bestScore) {
				bestScore = currentSolution.finalScore;
				bestSolution = currentSolution;
			}

			nbFullIterations++;
		}

		if (Player.isDebugOn) {
			String bestActionsString = "";
			for (int i = 0; i < bestSolution.actions.length; i++) {
				bestActionsString += "R" + i + " " + bestSolution.actions[i];
			}

			Print.debug("End PureRandomWithScoreAI with " + nbFullIterations + " full iterations, " + GameEngine.nbApplyAction + " iterations and score: " + bestScore);
			Print.debug(bestActionsString);
			Time.debugDuration("After AI");
		}

		// Update the action message, can be interesting for arena battles
		bestSolution.actions[0].message = "Nb: " + Print.formatIntFixedLenght(6, GameEngine.nbApplyAction);

		// And finally returns the first step of the best solution found in the allocated time
		return Collections.singletonList(bestSolution.actions[0]);
	}

	private Solution buildAndEvaluateRandomSolution(GameState gs, AI opAI, SolutionEvaluation eval) {

		Solution solution = new Solution(depth);

		// Important to copy, so that we don't alter the starting gamestate for the next iterations...
		GameState gsCopy = gs.copy();

		for (int i = 0; i < depth; i++) {

			if (gsCopy.gameResult == GameResult.UNKNOWN) {
				solution.actions[i] = getRandomAction(gsCopy);

				// No need to copy here, since we don't care at all about the intermediate gamestates
				GameEngine.applyActionWithoutCopy(gsCopy, solution.actions[i], opAI.computeIntact(gsCopy));
			}

			// Stores the score of this new gamestate
			solution.scores[i] = eval.getGameStateScore(gsCopy);
		}

		eval.computeFinalScore(solution);

		return solution;
	}

	private Action getRandomAction(GameState gsCopy) {
		// TODO Auto-generated method stub
		return null;
	}

}