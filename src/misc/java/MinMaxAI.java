import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Collections;

class MinMaxAI extends AI {

	private SolutionEvaluation eval;
	private int nbBreaks = 0;
	private double totalRatioPlayed = 0;
	private static final Random r = new Random();

	public MinMaxAI(SolutionEvaluation eval) {
		super();
		this.eval = eval;
	}

	@Override
	public List<Action> compute(GameState gs) {

		if (Player.isDebugOn) {
			Time.debugDuration("Starting MinMax AI");
		}

		Action result = null;
		Action expectedOpAction = null;
		double bestScore = -eval.maxScore;

		List<Action> myActions = getActions(gs, true);
		List<Action> opActions = getActions(gs, false);

		for (int i = 0; i < myActions.size(); i++) {
			Action myAction = myActions.get(i);
			if (Time.isTimeLeft(gs.round() == 1)) {
				double bestScoreForMyAction = eval.maxScore;
				Action tempExpectedOpAction = null;

				for (int j = -1; j < opActions.size(); j++) {
					Action opAction = null;
					if (j == -1) {
						if (expectedOpAction != null) {
							// Killer move from past branches, see https://en.wikipedia.org/wiki/Killer_heuristic
							opAction = expectedOpAction;
						} else {
							continue;
						}
					} else {
						opAction = opActions.get(j);
					}

					if (Time.isTimeLeft(gs.round() == 1)) {
						GameState gsAfterPlay = GameEngine.applyActionWithCopy(gs, myAction, opAction);
						double score = eval.getGameStateScore(gsAfterPlay);

						if (score < bestScoreForMyAction) {
							bestScoreForMyAction = score;
							tempExpectedOpAction = opAction;
						}

						if (bestScoreForMyAction < bestScore) {
							// Cut it, it's useless to continue
							if (Player.isDebugOn) {
								Print.debug("For my action " + myAction + ", cutting since " + bestScoreForMyAction + " < " + bestScore + ", obtained with op action: " + tempExpectedOpAction);
							}
							break;
						}
					} else {
						if (Player.isDebugOn) {
							Print.debug("Breaking it on op actions... " + j + " done out of " + opActions.size());
							Time.debugDuration("opActions loop");
						}
						break;
					}
				}

				if (bestScoreForMyAction > bestScore) {
					bestScore = bestScoreForMyAction;
					result = myAction;
					expectedOpAction = tempExpectedOpAction;
				}
			} else {
				if (Player.isDebugOn) {
					nbBreaks++;
					Print.debug("Breaking it on my actions... " + i + " done out of " + myActions.size());
					Time.debugDuration("myActions loop");
				}
				break;
			}

			if (Player.isDebugOn) {
				Print.debug("Best move so far: " + result + " with score: " + bestScore + " and op action: " + expectedOpAction);
			}

		}

		if (result == null) {
			// We loose whatever move
			if (Player.isDebugOn) {
				Print.debug("We'll loose whatever move, play 0");
			}
			result = null; // TODO: update with any valid move
		}

		double ratioPlayed = 100 * GameEngine.nbApplyAction / ((double) myActions.size() * opActions.size());
		totalRatioPlayed += ratioPlayed;

		if (Player.isDebugOn) {
			Time.debugDuration("After MinMax AI");
			Print.debug(String.format("%04d", GameEngine.nbApplyAction) + " iterations performed, on the expected " + String.format("%04d", myActions.size() * opActions.size())
					+ ", so ratio played is: " + String.format("%.2f", ratioPlayed) + "%");
			Print.debug("Best action found: " + result + " with score: " + bestScore);
			Print.debug("Expected op action: " + expectedOpAction);

			if (gs.round() == 100) { // TODO: update with final round number
				// Last turn, output some data
				double averageRatioPlayed = totalRatioPlayed / gs.round();
				Print.debug("Total number of sad breaks: " + nbBreaks);
				Print.debug("Average ratio played: " + String.format("%.2f", averageRatioPlayed) + "%");
			}

		}

		return Collections.singletonList(result);
	}

	private List<Action> getActions(GameState gs, boolean me) {

		List<Action> result = new ArrayList<>();
		// TODO: generate the actions (all ? sampling ? some random ?)
		return result;
	}

}