package engine.draw;

import static processing.core.PConstants.TWO_PI;

import engine.Engine;
import processing.core.PGraphics;
import processing.core.PVector;

/**
 * Cámara 2D con soporte de traslación, escala y rotación sobre un contexto {@link PGraphics}.
 *
 * <p>Orden de transformaciones en {@link #apply(PGraphics)}:
 * {@code rotate(rotation)} -> {@code scale(scale)} -> {@code translate(-position.x, -position.y)}
 * (redondeando a píxeles enteros si {@link Engine#isPixelPerfect()} está activo).
 */
public class Camera2D {

	// =========================================================================
	// Estado de Transformación
	// =========================================================================

	public final PVector position = new PVector(0, 0);
	public float scale = 1.0f;
	private float rotation = 0.0f;

	// =========================================================================
	// Rotación y Aplicación de Matriz
	// =========================================================================

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
