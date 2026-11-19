package engine.physics;

import engine.objects.GameObject;
import processing.core.PVector;
import processing.data.XML;

public class Box extends GameObject {

	public PVector position = new PVector();
	public PVector size = new PVector();
	public int layer = 1;
	public int mask = 1;
	public boolean disabled = false;

	public Box() {
		this.id = "box";
	}

	public Box(float x, float y, float w, float h) {
		this.id = "box";
		this.position.set(x, y);
		this.size.set(w, h);
	}

	public boolean intersects(Box other) {
		if (this.disabled || other == null || other.disabled) return false;
		return (
			position.x < other.position.x + other.size.x &&
			position.x + size.x > other.position.x &&
			position.y < other.position.y + other.size.y &&
			position.y + size.y > other.position.y
		);
	}

	// Game Object

	@Override
	public String getType() {
		return "Box";
	}

	public void fromXML(XML xml) {
		super.fromXML(xml);
		position.x = xml.getFloat("position-x", 0);
		position.y = xml.getFloat("position-y", 0);
		size.x = xml.getFloat("width", 0);
		size.y = xml.getFloat("height", 0);
		layer = xml.getInt("layer", 1);
		mask = xml.getInt("mask", 1);
		disabled = xml.getInt("disabled") == 1;
	}

	public XML toXML() {
		XML xml = super.toXML();
		xml.setName("Box");
		xml.setFloat("position-x", position.x);
		xml.setFloat("position-y", position.y);
		xml.setFloat("width", size.x);
		xml.setFloat("height", size.y);
		xml.setInt("layer", layer);
		xml.setInt("mask", mask);
		xml.setInt("disabled", disabled ? 1 : 0);
		return xml;
	}
}
