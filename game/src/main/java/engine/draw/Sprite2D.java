package engine.draw;

import static processing.core.PConstants.CENTER;

import engine.Engine;
import engine.ResourcesManager;
import engine.objects.GameObject;
import engine.objects.IDraw;
import processing.core.PGraphics;
import processing.core.PImage;
import processing.core.PVector;
import processing.data.XML;

public class Sprite2D extends GameObject implements Drawable, IDraw {

	public String resourcePath = "";
	private int _rows = 1;
	private int _cols = 1;

	public final PVector position = new PVector(0, 0, 0);
	public final PVector scale = new PVector(1, 1);
	public float rotation = 0.0f;

	public PImage[] frames;
	public int currentFrame = 0;
	public boolean visible = true;
	public float delay = 0.1f;
	public boolean centerPosition = false;

	private float _timeAccumulator = 0.0f;

	public Sprite2D() {
		this.id = "sprite";
	}

	public Sprite2D(String path, int rows, int cols, float zIndex) {
		this.id = "sprite";
		this.resourcePath = path;
		this._rows = rows;
		this._cols = cols;
		position.set(0, 0, zIndex);

		if (path.trim().isEmpty()) return;
		PImage img = ResourcesManager.loadImage(path);
		loadFrames(img, rows, cols);
	}

	public Sprite2D(String path, float zIndex) {
		this(path, 1, 1, zIndex);
	}

	// public void setOriginByPosition() {
	// 	if (frames == null || frames.length == 0) return;
	// 	position.x += (float) frames[0].width / 2;
	// 	position.y += (float) frames[0].height / 2;
	// }

	public boolean isVisible() {
		return visible;
	}

	public void show() {
		visible = true;
	}

	public void hide() {
		visible = false;
	}

	public float getZIndex() {
		return position.z;
	}

	public void updateDraw(float dt) {
		if (frames == null || frames.length <= 1) return;

		_timeAccumulator += dt;

		while (_timeAccumulator >= delay) {
			currentFrame = (currentFrame + 1) % frames.length;
			_timeAccumulator -= delay;
		}
	}

	public void draw(PGraphics g) {
		if (!visible || frames == null || frames.length == 0) return;
		if (currentFrame >= frames.length) currentFrame = frames.length - 1;
		if (currentFrame < 0) currentFrame = 0;

		g.push();

		float w = frames[0].width;
		float h = frames[0].height;

		float drawX;
		float drawY;
		if (centerPosition) {
			drawX = position.x;
			drawY = position.y;
		} else {
			drawX = position.x + (w * scale.x) / 2f;
			drawY = position.y + (h * scale.y) / 2f;
		}

		if (Engine.isPixelPerfect() && rotation == 0.0f) {
			g.translate(Math.round(drawX), Math.round(drawY));
		} else {
			g.translate(drawX, drawY);
		}

		g.rotate(rotation);
		g.scale(scale.x, scale.y);
		g.imageMode(CENTER);
		g.image(frames[currentFrame], 0, 0, w, h);

		g.pop();
	}

	// Game Object

	@Override
	public String getType() {
		return "Sprite2D";
	}

	public XML toXML() {
		XML xml = super.toXML();
		xml.setName("Sprite");
		xml.setString("path", resourcePath);
		xml.setFloat("position-x", position.x);
		xml.setFloat("position-y", position.y);
		xml.setFloat("z-index", position.z);
		xml.setInt("rows", _rows);
		xml.setInt("cols", _cols);
		xml.setFloat("scale-x", scale.x);
		xml.setFloat("scale-y", scale.y);
		xml.setFloat("rotation", rotation);
		xml.setInt("visible", visible ? 1 : 0);
		xml.setFloat("delay", delay);
		xml.setInt("center-position", centerPosition ? 1 : 0);
		return xml;
	}

	public void fromXML(XML xml) {
		super.fromXML(xml);

		resourcePath = xml.getString("path", "");

		position.x = xml.getFloat("position-x", 0);
		position.y = xml.getFloat("position-y", 0);
		position.z = xml.getFloat("z-index", 0);

		_rows = xml.getInt("rows", 1);
		_cols = xml.getInt("cols", 1);

		scale.x = xml.getFloat("scale-x", 1);
		scale.y = xml.getFloat("scale-y", 1);

		rotation = xml.getFloat("rotation", 0);
		visible = xml.getInt("visible", 1) == 1;
		delay = xml.getFloat("delay", 0.1f);
		centerPosition = xml.getInt("center-position", 1) == 1;

		if (!resourcePath.isEmpty()) {
			PImage img = ResourcesManager.loadImage(resourcePath);
			loadFrames(img, _rows, _cols);
		}

		float targetW = xml.getFloat("width", -1.0f);
		float targetH = xml.getFloat("height", -1.0f);
		if (targetW > 0.0f || targetH > 0.0f) {
			setPixelSize(targetW, targetH);
		}
	}

	/** Ajusta scale.x / scale.y para que cada frame mida exactamente targetW x targetH píxeles. */
	public void setPixelSize(float targetW, float targetH) {
		if (frames == null || frames.length == 0) return;
		float fw = Math.max(1.0f, frames[0].width);
		float fh = Math.max(1.0f, frames[0].height);
		if (targetW > 0.0f) scale.x = targetW / fw;
		if (targetH > 0.0f) scale.y = targetH / fh;
	}

	protected void loadFrames(PImage img, int rows, int cols) {
		if (img == null) {
			_cols = 0;
			_rows = 0;
			return;
		}

		_cols = cols;
		_rows = rows;

		float width = (float) img.width / _cols;
		float height = (float) img.height / _rows;
		PImage[] raw = new PImage[_cols * _rows];
		int lastNonEmpty = 0;
		for (int y = 0; y < _rows; y++) {
			for (int x = 0; x < _cols; x++) {
				int idx = x + y * _cols;
				PImage cell = img.get(
					(int) (x * width),
					(int) (y * height),
					(int) width,
					(int) height
				);
				raw[idx] = cell;
				cell.loadPixels();
				if (cell.pixels != null) {
					for (int px : cell.pixels) {
						if ((px >>> 24) != 0) {
							lastNonEmpty = idx;
							break;
						}
					}
				}
			}
		}
		int count = lastNonEmpty + 1;
		frames = new PImage[count];
		System.arraycopy(raw, 0, frames, 0, count);
	}
}
