package engine.objects;

/**
 * Contrato para objetos de escena con animación visual ({@link #updateDraw(float)})
 * y renderizado sobre el lienzo virtual ({@link #draw(processing.core.PGraphics)}).
 */
public interface IDraw {
	void updateDraw(float dt);
	void draw(processing.core.PGraphics g);
}
