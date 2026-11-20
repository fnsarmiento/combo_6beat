package engine.draw;

/**
 * Contrato para elementos visuales con orden de profundidad ({@code zIndex}) y visibilidad conmutable.
 */
public interface Drawable {
	float getZIndex();

	boolean isVisible();
	void show();
	void hide();
}
