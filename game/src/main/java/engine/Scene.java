package engine;

import controlP5.Button;
import controlP5.ControlFont;
import controlP5.ControlP5;
import controlP5.ControllerInterface;
import controlP5.Group;
import controlP5.Textarea;
import controlP5.Textlabel;
import engine.dialogs.DialogManager;
import engine.dialogs.GameDialog;
import engine.draw.Sprite2D;
import engine.objects.GameObject;
import engine.objects.IDraw;
import engine.objects.IUpdate;
import engine.physics.Box;
import java.util.ArrayList;
import java.util.List;
import processing.core.PApplet;
import processing.core.PGraphics;
import processing.data.XML;
import processing.event.KeyEvent;

/**
 * Clase base para todas las escenas del motor.
 *
 * <p>Gestiona la lista de {@link GameObject} activos, la carga declarativa desde archivos XML
 * de escena, la integración con {@link DialogManager} y el registro de controles {@link ControlP5}
 * para su destrucción automática al cerrar la escena.
 */
public class Scene {

	// =========================================================================
	// Estado Protegido de la Escena
	// =========================================================================

	protected ArrayList<String> _controllers = new ArrayList<>();
	protected ArrayList<GameObject> _gameObjects = new ArrayList<>();
	protected XML _xmlScene;
	protected float _defaultSpawnX = -1.0f;
	protected float _defaultSpawnY = -1.0f;

	// =========================================================================
	// Constructores
	// =========================================================================

	public Scene() {}

	/**
	 * Construye una escena cargando su definición XML mediante {@link ResourcesManager#loadMap(String)}.
	 */
	public Scene(String path) {
		if (path != null && !path.isEmpty()) {
			_xmlScene = ResourcesManager.loadMap(path);
		}
	}

	// =========================================================================
	// Gestión de GameObjects y Punto de Aparición (Spawn)
	// =========================================================================

	public void addGameObject(GameObject obj) {
		if (obj != null) {
			_gameObjects.add(obj);
		}
	}

	public void addGameObjectAt(int index, GameObject obj) {
		if (obj != null) {
			int idx = Math.max(0, Math.min(_gameObjects.size(), index));
			_gameObjects.add(idx, obj);
		}
	}

	public List<GameObject> getGameObjects() {
		return _gameObjects;
	}

	public void setDefaultSpawn(float x, float y) {
		_defaultSpawnX = x;
		_defaultSpawnY = y;
	}

	public float getDefaultSpawnX() {
		return _defaultSpawnX;
	}

	public float getDefaultSpawnY() {
		return _defaultSpawnY;
	}

	// =========================================================================
	// Helpers de Interfaz de Usuario (ControlP5 escalado a resolución física)
	// =========================================================================

	/**
	 * Registra un controlador {@link ControlP5} para eliminarlo automáticamente en {@link #close()}.
	 */
	public void registerUI(ControllerInterface<?> control) {
		if (control != null) {
			_controllers.add(control.getName());
		}
	}

	/**
	 * Convierte una coordenada horizontal en espacio virtual (ej. base 480) a píxeles de ventana física.
	 */
	protected float scaleW(float val) {
		PApplet app = Engine.getApp();
		PGraphics vs = Engine.getVirtualScreen();
		float baseW = (vs != null && vs.width > 0) ? vs.width : 480.0f;
		return (app != null) ? (val / baseW) * app.width : val;
	}

	/**
	 * Convierte una coordenada vertical en espacio virtual (ej. base 270) a píxeles de ventana física.
	 */
	protected float scaleH(float val) {
		PApplet app = Engine.getApp();
		PGraphics vs = Engine.getVirtualScreen();
		float baseH = (vs != null && vs.height > 0) ? vs.height : 270.0f;
		return (app != null) ? (val / baseH) * app.height : val;
	}

	protected ControlFont makeFont(float baseSize, boolean useTitle) {
		var pfont = useTitle
			? (GameDialog.titleFont != null ? GameDialog.titleFont : GameDialog.descriptionFont)
			: GameDialog.descriptionFont;
		if (pfont == null) return null;
		return new ControlFont(pfont, Math.max(8, (int) scaleH(baseSize)));
	}

	protected Group addUIGroup(String id, float x, float y, float w, float h) {
		Group g = Engine.getControlP5().addGroup(id)
			.setPosition(scaleW(x), scaleH(y))
			.setWidth(Math.max(1, (int) scaleW(w)))
			.setHeight(Math.max(1, (int) scaleH(h)))
			.hideBar();
		registerUI(g);
		return g;
	}

	protected Button addUIBox(String id, Group group, float x, float y, float w, float h, int bgCol) {
		Button b = Engine.getControlP5().addButton(id)
			.setLabel("")
			.setPosition(scaleW(x), scaleH(y))
			.setSize(Math.max(1, (int) scaleW(w)), Math.max(1, (int) scaleH(h)))
			.setColorBackground(bgCol);
		if (group != null) b.setGroup(group);
		b.lock();
		registerUI(b);
		return b;
	}

	protected Button addUIButton(
		String id,
		Group group,
		String label,
		float x,
		float y,
		float w,
		float h,
		int bgCol,
		int fgCol,
		ControlFont font,
		Runnable onClick
	) {
		Button b = Engine.getControlP5().addButton(id)
			.setLabel(label != null ? label : "")
			.setPosition(scaleW(x), scaleH(y))
			.setSize(Math.max(1, (int) scaleW(w)), Math.max(1, (int) scaleH(h)))
			.setColorBackground(bgCol)
			.setColorForeground(fgCol);
		if (group != null) b.setGroup(group);
		b.getCaptionLabel().toUpperCase(false);
		if (font != null) b.setFont(font);
		if (onClick != null) b.onRelease(e -> onClick.run());
		registerUI(b);
		return b;
	}

	protected Textlabel addUILabel(String id, Group group, String text, float x, float y, int col, ControlFont font) {
		Textlabel lbl = Engine.getControlP5().addTextlabel(id)
			.setText(text != null ? text : "")
			.setPosition(scaleW(x), scaleH(y))
			.setColorValue(col);
		if (group != null) lbl.setGroup(group);
		if (font != null) lbl.setFont(font);
		registerUI(lbl);
		return lbl;
	}

	protected Textarea addUITextarea(
		String id,
		Group group,
		String text,
		float x,
		float y,
		float w,
		float h,
		int textCol,
		int bgCol,
		ControlFont font
	) {
		Textarea area = Engine.getControlP5().addTextarea(id)
			.setPosition(scaleW(x), scaleH(y))
			.setSize(Math.max(1, (int) scaleW(w)), Math.max(1, (int) scaleH(h)))
			.setColorValue(textCol)
			.setColorBackground(bgCol)
			.setText(text != null ? text : "");
		if (group != null) area.setGroup(group);
		if (font != null) area.setFont(font);
		registerUI(area);
		return area;
	}

	// =========================================================================
	// Ciclo de Vida y Parseo XML de la Escena
	// =========================================================================

	public void setup() {
		DialogManager.setOnLoadRequest(this::onDialogLoadRequest);

		if (_xmlScene != null) {
			float sx = _xmlScene.getFloat("spawn-x", -1.0f);
			float sy = _xmlScene.getFloat("spawn-y", -1.0f);
			if (sx >= 0.0f && sy >= 0.0f) {
				setDefaultSpawn(sx, sy);
			}

			parseBaseNodes(_xmlScene);

			String dialogFile = _xmlScene.getString("dialogs", "");
			if (!dialogFile.isEmpty()) {
				DialogManager.loadDialogFile(dialogFile);
				String startDialog = _xmlScene.getString("start-dialog", "");
				if (!startDialog.isEmpty()) {
					DialogManager.start(startDialog);
				}
			}
		}
	}

	/**
	 * Parsea los nodos base del XML ({@code Box}/{@code box}/{@code Rect} y {@code Sprite}/{@code Sprite2D})
	 * y delega cualquier otra etiqueta a {@link #parseCustomNode(XML)}.
	 */
	protected void parseBaseNodes(XML scene_parent) {
		for (XML child : scene_parent.getChildren()) {
			String tag = child.getName();
			if (tag == null || tag.startsWith("#")) continue;

			switch (tag) {
				case "Box", "box", "Rect" -> {
					Box b = ResourcesManager.loadBox(child);
					_gameObjects.add(b);
				}
				case "Sprite", "Sprite2D" -> {
					Sprite2D spr = ResourcesManager.loadSprite(child);
					_gameObjects.add(spr);
				}
				default -> parseCustomNode(child);
			}
		}
	}

	/**
	 * Punto de extensión para que las subclases interpreten etiquetas XML específicas del juego.
	 */
	protected void parseCustomNode(XML node) {}

	/**
	 * Actualiza el diálogo activo (pausando los objetos de la escena mientras esté abierto)
	 * o todos los {@link GameObject} registrados que implementen {@link IUpdate} / {@link IDraw}.
	 */
	public void update(float deltaTime) {
		if (DialogManager.isActive()) {
			DialogManager.update(deltaTime);
			return;
		}

		for (GameObject obj : _gameObjects) {
			if (obj instanceof IUpdate) {
				((IUpdate) obj).update(deltaTime);
			}
			if (obj instanceof IDraw) {
				((IDraw) obj).updateDraw(deltaTime);
			}
		}
	}

	public void draw(PGraphics g) {
		for (GameObject obj : _gameObjects) {
			if (obj instanceof IDraw) {
				((IDraw) obj).draw(g);
			}
		}
	}

	public void keyEvent(KeyEvent e) {}

	protected void onDialogLoadRequest(String scenePath) {
		String absPath = ResourcesManager.getAbsPathFromProject(scenePath);
		Engine.changeScene(new Scene(absPath));
	}

	/**
	 * Libera callbacks de diálogo, remueve todos los controladores {@link ControlP5} registrados
	 * por esta escena y vacía la lista de objetos.
	 */
	public void close() {
		DialogManager.setOnLoadRequest(null);
		DialogManager.close();
		ControlP5 cp5 = Engine.getControlP5();

		for (int i = _controllers.size() - 1; i >= 0; i--) {
			try {
				cp5.remove(_controllers.get(i));
			} catch (Exception ignored) {}
		}
		_controllers.clear();
		_gameObjects.clear();
	}
}
