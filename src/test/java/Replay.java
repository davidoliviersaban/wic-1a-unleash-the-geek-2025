import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

public class Replay {

	private JSONObject json;
	public int turnCount;
	public int gameId;
	public int playerCount;

	private static final String json_scores = "scores";
	private static final String json_turns = "turns";
	private static final String json_myPosition = "myPosition";
	private static final String json_outputs = "outputs";
	private static final String json_gameState = "gameState";

	public Replay(File file) throws IOException {
		InputStream is = new FileInputStream(file);
		String jsonTxt = IOUtils.toString(is, "UTF-8");
		this.json = new JSONObject(jsonTxt);
		this.gameId = Integer.valueOf(file.getName().substring(0, file.getName().indexOf('.')));
		this.playerCount = json.getJSONArray(json_scores).length();
		this.turnCount = json.getJSONArray(json_turns).length();
	}

	public int getMyPosition() {
		return json.getInt(json_myPosition);
	}

	public String getMyOutput(int turn, int myPosition) {
		String result = null;
		JSONArray ouputs = json.getJSONArray(json_turns).getJSONObject(turn).getJSONArray(json_outputs);
		if (ouputs.length() >= myPosition + 1 && !ouputs.getJSONArray(myPosition).isEmpty()) {
			result = ouputs.getJSONArray(myPosition).getString(0);
		}
		return result;
	}

	public String getOpponentOutput(int turn, int myPosition) {
		String result = null;
		JSONArray ouputs = json.getJSONArray(json_turns).getJSONObject(turn).getJSONArray(json_outputs);
		if (ouputs.length() >= (myPosition + 1) % playerCount + 1 && !ouputs.getJSONArray((myPosition + 1) % playerCount).isEmpty()) {
			result = ouputs.getJSONArray((myPosition + 1) % playerCount).getString(0);
		}
		return result;
	}

	public String[] getGameStateString(int turn) {
		String[] result = new String[json.getJSONArray(json_turns).getJSONObject(turn).getJSONArray(json_gameState).length()];

		for (int i = 0; i < result.length; i++) {
			result[i] = json.getJSONArray(json_turns).getJSONObject(turn).getJSONArray(json_gameState).getString(i);
		}

		return result;
	}

	public GameResult getGameResult() {

		GameResult result = null;

		int myScore = json.getJSONArray(json_scores).getInt(getMyPosition());
		int opScore = json.getJSONArray(json_scores).getInt((getMyPosition() + 1) % 2);

		if (myScore > opScore) {
			result = GameResult.WON;
		} else if (myScore < opScore) {
			result = GameResult.LOST;
		} else {
			result = GameResult.TIE;
		}

		return result;
	}

}