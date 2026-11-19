package engine.dialogs;

import controlP5.ControlP5;
import controlP5.Group;
import controlP5.Textarea;
import controlP5.ControlFont;
import engine.Engine;
import engine.KBInput;
import processing.core.PFont;
import processing.core.PImage;

import static engine.dialogs.DialogManager.ResponseType;
import static processing.core.PApplet.min;

public class GameDialog {
	private static final int BG_COLOR	= Engine.getApp().color(0, 0, 0, 200);
	private static final int DESC_COLOR  = Engine.getApp().color(255);
	private static final int COLOR_IDLE  = Engine.getApp().color(0, 45, 90);
	private static final int COLOR_HOVER = Engine.getApp().color(0, 116, 217);

	// Resolución base para escalar la UI dentro de engine sin depender de game.Global
	public static float BASE_WIDTH = 480f;
	public static float BASE_HEIGHT = 270f;

	// Teclas configurables desde el juego (Por defecto: E/Enter, A/Izquierda, D/Derecha)
	public static int[] KEYS_INTERACT = { 'E', 'Z', '\n' };
	public static int[] KEYS_LEFT	 = { 'A', 37 }; // 37 = LEFT arrow
	public static int[] KEYS_RIGHT	= { 'D', 39 }; // 39 = RIGHT arrow

	public int titleColor = Engine.getApp().color(255);

	public static PFont titleFont;
	public static PFont descriptionFont;
	public static int titleSize = 16;
	public static int descriptionSize = 14;

	public boolean visible = true;
	public PImage background;
	public String title, description;
	public String option1, option2;

	private int descriptionAnimationIndex = 0;
	public static final float DELAY = 0.035f;
	private float elapsed = 0.0f;

	private Group dialogGroup;
	private Textarea descArea;
	private int selectedOption = 1;
	private ResponseType response = ResponseType.None;

	public GameDialog(String title, String description, String option1, String option2) { setContent(title, description, option1, option2); }

	public GameDialog(String title, String description, String option1) { this(title, description, option1, null); }

	public GameDialog(String title, String description) { this(title, description, null, null); }

	private static float scaleW(float val) { return (val / BASE_WIDTH) * Engine.getApp().width; }

	private static float scaleH(float val) { return (val / BASE_HEIGHT) * Engine.getApp().height; }

	private static boolean isAnyKeyJustPressed(int[] keys) {
		for (int k : keys) {
			if (KBInput.wasKeyJustPressed(k)) return true;
		}
		return false;
	}

	public void setContent(String title, String description, String option1, String option2) {
		this.title = title != null ? title : "";
		this.description = description != null ? description : "";
		this.option1 = (option1 != null && !option1.isEmpty()) ? option1 : null;
		this.option2 = (option2 != null && !option2.isEmpty()) ? option2 : null;

		this.descriptionAnimationIndex = 0;
		this.elapsed = 0.0f;
		this.selectedOption = 1;
		this.response = ResponseType.None;

		setupUI();
	}

	private void setupUIBackground(ControlP5 cp5, int groupW, int groupH) {
		var bgBtn = cp5.addButton("ui_dialog_bg")
				.setPosition(0, 0)
				.setSize(groupW, groupH)
				.setGroup(dialogGroup);

		if (background != null) {
			bgBtn.setImage(background);
		} else {
			bgBtn.setColorBackground(BG_COLOR);
		}

		if (option1 == null) {
			bgBtn.onRelease(e -> {
				if (response == ResponseType.None) optionPressed();
			});
		} else {
			bgBtn.lock();
		}
	}

	private void setupUITitleDescription(ControlP5 cp5) {
		if (title != null && !title.isEmpty()) {
			var titleLabel = cp5.addTextlabel("ui_dialog_title")
					.setText(title)
					.setPosition((int) scaleW(5), (int) scaleH(5))
					.setColorValue(titleColor)
					.setGroup(dialogGroup);

			if (titleFont != null) {
				int scaledTitleSize = (int) scaleH(titleSize);
				titleLabel.setFont(new ControlFont(titleFont, scaledTitleSize));
			}
		}

		int descW = (int) scaleW(420);
		int descH = (int) scaleH(60);

		descArea = cp5.addTextarea("ui_dialog_desc")
				.setPosition((int) scaleW(10), (int) scaleH(20))
				.setSize(descW, descH)
				.setColorValue(DESC_COLOR)
				.setText("")
				.setGroup(dialogGroup);

		if (descriptionFont != null) {
			int scaledDescSize = (int) scaleH(descriptionSize);
			descArea.setFont(new ControlFont(descriptionFont, scaledDescSize));
		}
	}

	private void setupUIOptions(ControlP5 cp5, int groupW) {
		if (option1 == null) return;

		int btnW = (int) scaleW(80);
		int btnH = (int) scaleH(25);
		int btnY = (int) scaleH(80);
		float centerX = groupW / 2.0f;
		int offset40 = (int) scaleW(40);

		if (option2 != null) {
			cp5.addButton("ui_dialog_opt1")
					.setLabel(option1)
					.setSize(btnW, btnH)
					.setPosition(centerX - btnW - offset40, btnY)
					.setGroup(dialogGroup)
					.onRelease(e -> {
						if (response == ResponseType.None) {
							if (!isTypingFinished()) completeTyping();
							else response = ResponseType.Option1;
						}
					});

			cp5.addButton("ui_dialog_opt2")
					.setLabel(option2)
					.setSize(btnW, btnH)
					.setPosition(centerX + offset40, btnY)
					.setGroup(dialogGroup)
					.onRelease(e -> {
						if (response == ResponseType.None) {
							if (!isTypingFinished()) completeTyping();
							else response = ResponseType.Option2;
						}
					});

			updateButtonVisuals();
		} else {
			cp5.addButton("ui_dialog_opt1")
					.setLabel(option1)
					.setSize(btnW, btnH)
					.setPosition(centerX - (btnW / 2.0f), btnY)
					.setGroup(dialogGroup)
					.onRelease(e -> {
						if (response == ResponseType.None) {
							if (!isTypingFinished()) completeTyping();
							else response = ResponseType.Option1;
						}
					});
		}
	}

	private void setupUI() {
		ControlP5 cp5 = Engine.getControlP5();
		destroy();

		int groupW = (int) scaleW(440);
		int groupH = (int) scaleH(110);
		int startX = (int) scaleW(20);
		int startY = (int) scaleH(150);

		dialogGroup = cp5.addGroup("ui_dialog_group")
				.setPosition(startX, startY)
				.setWidth(groupW)
				.setHeight(groupH)
				.hideBar();

		setupUIBackground(cp5, groupW, groupH);
		setupUITitleDescription(cp5);
		setupUIOptions(cp5, groupW);

		updateButtonVisuals();

		if (!visible) dialogGroup.hide();
		else dialogGroup.show();
	}

	public void destroy() {
		ControlP5 cp5 = Engine.getControlP5();
		if (cp5 != null) {
			if (cp5.getController("ui_dialog_bg") != null) cp5.remove("ui_dialog_bg");
			if (cp5.getController("ui_dialog_title") != null) cp5.remove("ui_dialog_title");
			if (cp5.getController("ui_dialog_desc") != null) cp5.remove("ui_dialog_desc");
			if (cp5.getController("ui_dialog_opt1") != null) cp5.remove("ui_dialog_opt1");
			if (cp5.getController("ui_dialog_opt2") != null) cp5.remove("ui_dialog_opt2");
			if (cp5.getGroup("ui_dialog_group") != null) cp5.remove("ui_dialog_group");
		}
		dialogGroup = null;
		descArea = null;
	}

	private void updateButtonVisuals() {
		ControlP5 cp5 = Engine.getControlP5();
		var opt1 = cp5.getController("ui_dialog_opt1");
		var opt2 = cp5.getController("ui_dialog_opt2");

		if (opt1 != null && opt2 != null) {
			opt1.setColorBackground(selectedOption == 1 ? COLOR_HOVER : COLOR_IDLE);
			opt2.setColorBackground(selectedOption == 2 ? COLOR_HOVER : COLOR_IDLE);
		}
	}

	private boolean isTypingFinished() { return descriptionAnimationIndex >= description.length(); }

	private void completeTyping() {
		descriptionAnimationIndex = description.length();
		if (descArea != null) descArea.setText(description);
	}

	private void updateAnimation(float dt) {
		if (!visible || isTypingFinished()) return;

		elapsed += dt;
		while (elapsed >= DELAY && !isTypingFinished()) {
			descriptionAnimationIndex = min(description.length(), descriptionAnimationIndex + 1);
			elapsed -= DELAY;
		}

		if (descArea != null) {
			descArea.setText(description.substring(0, descriptionAnimationIndex));
		}
	}

	public void update(float dt) {
		updateAnimation(dt);

		if (response != ResponseType.None) return;

		if (isAnyKeyJustPressed(KEYS_INTERACT)) {
			optionPressed();
		}

		if (option1 != null && option2 != null) {
			changeFocusOption();
		}
	}

	private void optionPressed() {
		if (!isTypingFinished()) {
			completeTyping();
			return;
		}

		if (option1 == null) {
			response = ResponseType.Neutral;
		} else if (option2 == null) {
			response = ResponseType.Option1;
		} else {
			response = (selectedOption == 1) ? ResponseType.Option1 : ResponseType.Option2;
		}
	}

	private void changeFocusOption() {
		boolean leftPressed  = isAnyKeyJustPressed(KEYS_LEFT);
		boolean rightPressed = isAnyKeyJustPressed(KEYS_RIGHT);

		if (leftPressed && selectedOption != 1) {
			selectedOption = 1;
			updateButtonVisuals();
		} else if (rightPressed && selectedOption != 2) {
			selectedOption = 2;
			updateButtonVisuals();
		}
	}

	public boolean hasResponse() { return response != ResponseType.None; }

	public ResponseType getResponse() {	return response; }
}
