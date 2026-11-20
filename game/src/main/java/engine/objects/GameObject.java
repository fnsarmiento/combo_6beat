package engine.objects;

import engine.draw.Sprite2D;
import engine.physics.Box;
import processing.data.XML;

/**
 * Entidad base serializable a/desde XML dentro de una {@link engine.Scene}.
 *
 * <p>Provee identificación ({@code id}), agrupación lógica ({@code group}) y utilidades
 * protegidas para sincronizar y serializar componentes hijos ({@link Box}, {@link Sprite2D}).
 */
public abstract class GameObject {

	// =========================================================================
	// Identificación y Agrupación
	// =========================================================================

	public String id = "gameobject";
	public String group = "Root";

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getGroup() {
		return group;
	}

	public void setGroup(String group) {
		this.group = group;
	}

	public String getType() {
		return "GameObject";
	}

	// =========================================================================
	// Serialización XML Base y Helpers para Subclases Compuestas
	// =========================================================================

	public XML toXML() {
		XML xml = new XML("GameObject");
		xml.setString("id", id);
		xml.setString("group", group);
		xml.setString("type", getType());
		return xml;
	}

	public void fromXML(XML xml) {
		id = xml.getString("id", id);
		group = xml.getString("group", group);
	}

	protected static void syncSpriteToBox(Sprite2D sprite, Box box) {
		if (sprite != null && box != null && (box.size.x > 0 || box.size.y > 0)) {
			sprite.position.set(
				box.position.x + box.size.x / 2f,
				box.position.y + box.size.y / 2f,
				box.position.y
			);
		}
	}

	protected static void appendBoxXML(XML parent, Box box, String tagName) {
		if (parent == null || box == null) return;
		XML child = box.toXML();
		child.setName(tagName);
		parent.addChild(child);
	}

	protected static Box readBoxXML(XML parent, String tagName, Box current) {
		if (parent == null) return current;
		XML child = parent.getChild(tagName);
		if (child == null) return current;
		Box target = (current != null) ? current : new Box(0, 0, 0, 0);
		target.fromXML(child);
		return target;
	}

	protected static Sprite2D readSpriteXML(XML parent, Sprite2D current) {
		if (parent == null) return current;
		XML child = parent.getChild("Sprite");
		if (child == null) return current;
		Sprite2D target = (current != null) ? current : new Sprite2D("", 0);
		target.fromXML(child);
		return target;
	}

	protected static boolean parseBoolAttr(XML xml, String attr, boolean defaultVal) {
		if (xml == null) return defaultVal;
		String val = xml.getString(attr, defaultVal ? "1" : "0");
		if (val == null || val.isEmpty()) return defaultVal;
		return "1".equals(val) || "true".equalsIgnoreCase(val);
	}
}
