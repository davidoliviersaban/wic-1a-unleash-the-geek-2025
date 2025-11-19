import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.beans.Transient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

public class TestAI {

	public AI ai;

	@BeforeEach()
	public void init() {
		// ai = new AggressiveAI();
	}

	// Seed:
	// Replay:
	// expect to have this test failed
	// @Test
	public void test() {

		String[] inputMatchString = {
			"valid input string"
		};
		if (inputMatchString.length == 0) {
			throw new IllegalArgumentException("No input match string");
		}
		Scanner in = new Scanner(inputMatchString[0]);
		// Player.initMatch(in);
		GameState gs = Player.initRound(in);
		// Map<Tile, List<Zone>> map = AggressiveAI.splitZone(gs, gs.zones.get(0));
		// List<Action> actions = myAi.compute(gs);
		Print.debug(gs.toString());
	}


}