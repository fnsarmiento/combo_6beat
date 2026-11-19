package engine.draw;

public interface Drawable {
	float getZIndex();

	boolean isVisible();
	void show();
	void hide();
}
