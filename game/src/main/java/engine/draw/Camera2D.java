package engine.draw;

import engine.Engine;
import processing.core.PVector;
import processing.core.PGraphics;
import static processing.core.PConstants.TWO_PI;

public class Camera2D {
	public final PVector position = new PVector(0, 0);
	public float scale = 1.0f;
	private float rotation = 0.0f;

	public void setRotation(float r) { 
		this.rotation = r % TWO_PI; 
	}
	
	public float getRotation() { 
		return this.rotation; 
	}

	public void apply(PGraphics g) {
		g.pushMatrix();
		g.rotate(rotation);
		g.scale(scale);
		if (Engine.isPixelPerfect()) {
			g.translate(-Math.round(position.x), -Math.round(position.y));
		} else {
			g.translate(-position.x, -position.y);
		}
	}

	public void reset(PGraphics g) {
		g.popMatrix();
	}
}