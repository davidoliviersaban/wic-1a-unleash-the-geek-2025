public class Solution {

	public Action[] actions;
	public double[] scores;
	public double finalScore;

	public Solution(int depth) {
		actions = new Action[depth];
		scores = new double[depth];
	}

}

abstract class SolutionEvaluation {

	protected double maxScore;
	protected double patience;

	public SolutionEvaluation(double maxScore, double patience) {
		super();
		this.maxScore = maxScore;
		this.patience = patience;
	}

	// The score of a given GameState
	public double getGameStateScore(GameState gs) {

		double score = 0;

		switch (gs.gameResult) {
		case WON:
			score = maxScore;
			break;
		case LOST:
			score = -maxScore;
			break;
		case TIE:
			score = 0;
			break;
		case UNKNOWN:
			score = getUnknownGameStateScore(gs);
			break;
		default:
			break;
		}

		return score;
	}

	// The part to be implemented
	protected abstract double getUnknownGameStateScore(GameState gs);

	// The final evaluation of a full solution
	public void computeFinalScore(Solution solution) {

		for (int i = 0; i < solution.scores.length; i++) {
			solution.finalScore += solution.scores[i] * Math.pow(patience, i);
		}
	}

}

class SampleSolutionEvaluation extends SolutionEvaluation {

	private static final double maxScore = Double.MAX_VALUE;
	private static final double patience = 0.8;

	public SampleSolutionEvaluation() {
		super(maxScore, patience);
	}

	@Override
	protected double getUnknownGameStateScore(GameState gs) {
		// TODO Auto-generated method stub, use your knowledge and analysis of the game here :)
		return 0;
	}

}