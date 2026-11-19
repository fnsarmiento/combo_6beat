package engine.physics;

import java.util.ArrayList;
import processing.core.PVector;

public class CharacterBody extends Box {

	public ArrayList<Box> collidingBoxes = new ArrayList<>();
	public final PVector velocity = new PVector();

	public CharacterBody(float x, float y, float w, float h) {
		super(x, y, w, h);
	}

	public void moveAndSlide(float dt) {
		if (velocity.x == 0 && velocity.y == 0) return;

		float motionX = velocity.x * dt;
		float motionY = velocity.y * dt;
		float maxMotion = Math.max(Math.abs(motionX), Math.abs(motionY));
		int steps = Math.max(1, (int) Math.ceil(maxMotion));
		float stepDt = dt / steps;

		for (int i = 0; i < steps; i++) {
			moveXAxis(velocity.x * stepDt, collidingBoxes);
			moveYAxis(velocity.y * stepDt, collidingBoxes);
		}
	}

	private void moveXAxis(float amount, ArrayList<Box> walls) {
		if (amount == 0) return;

		float originalX = position.x;
		position.x += amount;

		if (collidesWithAny(walls)) {
			position.x = originalX;
		}
	}

	private void moveYAxis(float amount, ArrayList<Box> walls) {
		if (amount == 0) return;

		float originalY = position.y;
		position.y += amount;

		if (collidesWithAny(walls)) {
			position.y = originalY;
		}
	}

	private boolean collidesWithAny(ArrayList<Box> walls) {
		for (Box wall : walls) {
			if (this.intersects(wall)) return true;
		}
		return false;
	}

	@Override
	public String getType() {
		return "CharacterBody";
	}
}
