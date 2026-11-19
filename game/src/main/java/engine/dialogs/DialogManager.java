package engine.dialogs;

import engine.ResourcesManager;
import engine.audio.Audio;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import kuusisto.tinysound.Sound;
import processing.data.JSONObject;

public class DialogManager {
	public enum ResponseType { None, Neutral, Option1, Option2 }

	private static JSONObject _file;
	private static String _loadedFilePath = "";
	private static String _currentDialogID;
	private static String _currentContextKey = "";
	private static String _lastLoadedContextKey = "";
	private static String _savedStartID;
	private static String _savedLoseStartID;
	private static final HashMap<String, String> _savedNextByContext = new HashMap<>();
	private static final HashMap<String, String> _savedLoseNextByContext = new HashMap<>();
	private static final HashSet<String> _localFlags = new HashSet<>();
	private static GameDialog _activeDialog;
	private static boolean _promotedOption2To1 = false;

	// Callbacks desacoplados para la escena actual
	private static Consumer<String> onLoadRequest;
	private static BiConsumer<String, Integer> onGiveItemRequest;
	private static Consumer<String> onDefineFlag;
	private static Predicate<String> onCheckFlag;

	public static void setOnLoadRequest(Consumer<String> callback) { onLoadRequest = callback; }

	public static void setOnGiveItemRequest(BiConsumer<String, Integer> callback) { onGiveItemRequest = callback; }

	public static void setOnDefineFlag(Consumer<String> callback) { onDefineFlag = callback; }

	public static void setOnCheckFlag(Predicate<String> callback) { onCheckFlag = callback; }

	public static void loadDialogFile(String path) {
		close();
		if (path == null || path.isEmpty()) return;

		_file = ResourcesManager.loadJSON(path);
		_loadedFilePath = path;
		_currentDialogID = null;
	}

	public static String getLoadedFilePath() { return _loadedFilePath; }

	public static boolean start(String name) { return start(name, ""); }

	public static boolean start(String name, String contextKey) {
		if (_file == null || name == null || name.isEmpty()) return false;

		_currentContextKey = (contextKey != null) ? contextKey : "";

		String targetID = null;
		if (!_currentContextKey.isEmpty() && _savedNextByContext.containsKey(_currentContextKey)) {
			targetID = _savedNextByContext.remove(_currentContextKey);
			_savedLoseNextByContext.remove(_currentContextKey);
		} else if (_savedStartID != null && !_savedStartID.isEmpty()) {
			targetID = _savedStartID;
			_savedStartID = null;
			_savedLoseStartID = null;
		} else {
			targetID = name;
		}

		return showNode(targetID);
	}

	public static boolean switchTo(String dialogID) {
		if (_file == null || dialogID == null || dialogID.isEmpty()) {
			close();
			return false;
		}
		return showNode(dialogID);
	}

	public static boolean isActive() { return _activeDialog != null; }

	public static String getCurrentDialogID() {	return _currentDialogID; }

	public static GameDialog getActiveDialog() { return _activeDialog; }

	public static boolean update(float dt) {
		if (_activeDialog == null) return false;

		_activeDialog.update(dt);

		if (_activeDialog.hasResponse()) {
			advance(_activeDialog.getResponse());
		}

		return _activeDialog != null;
	}

	public static void close() {
		if (_activeDialog != null) {
			_activeDialog.destroy();
			_activeDialog = null;
		}
		_currentDialogID = null;
		_promotedOption2To1 = false;
	}

	private static boolean checkFlag(String flag) {
		if (flag == null || flag.isEmpty()) return true;
		if (onCheckFlag != null) return onCheckFlag.test(flag);
		return _localFlags.contains(flag);
	}

	private static void defineFlag(String flag) {
		if (flag == null || flag.isEmpty()) return;
		_localFlags.add(flag);
		if (onDefineFlag != null) {
			onDefineFlag.accept(flag);
		}
	}

	public static void clearLocalFlag(String flag) {
		if (flag != null && !flag.isEmpty()) {
			_localFlags.remove(flag);
		}
	}

	public static void resolveBattleOutcome(boolean won) {
		if (won) {
			defineFlag("LAST_BATTLE_WON");
			clearLocalFlag("LAST_BATTLE_LOST");
			if (_lastLoadedContextKey != null && !_lastLoadedContextKey.isEmpty()) {
				_savedLoseNextByContext.remove(_lastLoadedContextKey);
			} else {
				_savedLoseStartID = null;
			}
		} else {
			defineFlag("LAST_BATTLE_LOST");
			clearLocalFlag("LAST_BATTLE_WON");
			if (_lastLoadedContextKey != null && !_lastLoadedContextKey.isEmpty()) {
				String loseTarget = _savedLoseNextByContext.remove(_lastLoadedContextKey);
				if (loseTarget != null && !loseTarget.isEmpty()) {
					_savedNextByContext.put(_lastLoadedContextKey, loseTarget);
				} else {
					_savedNextByContext.remove(_lastLoadedContextKey);
				}
			} else {
				if (_savedLoseStartID != null && !_savedLoseStartID.isEmpty()) {
					_savedStartID = _savedLoseStartID;
				} else {
					_savedStartID = null;
				}
				_savedLoseStartID = null;
			}
		}
		_lastLoadedContextKey = "";
	}

	private static boolean isBranchAllowed(JSONObject branch) {
		if (branch == null) return true;
		String ifdef = branch.getString("__ifdef__", "");
		if (!ifdef.isEmpty() && !checkFlag(ifdef)) {
			return false;
		}
		String ifndef = branch.getString("__ifndef__", "");
		if (!ifndef.isEmpty() && checkFlag(ifndef)) {
			return false;
		}
		return true;
	}

	private static boolean showNode(String targetID) {
		JSONObject obj = _file.getJSONObject(targetID);
		if (obj == null) {
			close();
			return false;
		}

		String redirectIfDefFlag = obj.getString("__ifdef__", "");
		String redirectIfDefTarget = obj.getString("__next_ifdef__", "");
		if (!redirectIfDefFlag.isEmpty() && !redirectIfDefTarget.isEmpty() && checkFlag(redirectIfDefFlag)) {
			if (!targetID.equals(redirectIfDefTarget)) {
				return showNode(redirectIfDefTarget);
			}
		}
		String redirectIfNDefFlag = obj.getString("__ifndef__", "");
		String redirectIfNDefTarget = obj.getString("__next_ifndef__", "");
		if (!redirectIfNDefFlag.isEmpty() && !redirectIfNDefTarget.isEmpty() && !checkFlag(redirectIfNDefFlag)) {
			if (!targetID.equals(redirectIfNDefTarget)) {
				return showNode(redirectIfNDefTarget);
			}
		}

		_currentDialogID = targetID;
		_promotedOption2To1 = false;

		String title = obj.getString("title", "");
		String desc = obj.getString("desc", "");
		JSONObject op1 = obj.getJSONObject("option1");
		JSONObject op2 = obj.getJSONObject("option2");
		String option1 = (op1 != null && isBranchAllowed(op1)) ? op1.getString("text", null) : null;
		String option2 = (op2 != null && isBranchAllowed(op2)) ? op2.getString("text", null) : null;

		if ((option1 == null || option1.isEmpty()) && option2 != null && !option2.isEmpty()) {
			option1 = option2;
			option2 = null;
			_promotedOption2To1 = true;
		}

		if (_activeDialog == null) {
			_activeDialog = new GameDialog(title, desc, option1, option2);
		} else {
			_activeDialog.setContent(title, desc, option1, option2);
		}
		return true;
	}

	private static void advance(ResponseType response) {
		if (_file == null || _currentDialogID == null) {
			close();
			return;
		}

		ResponseType effectiveResponse = response;
		if (_promotedOption2To1 && response == ResponseType.Option1) {
			effectiveResponse = ResponseType.Option2;
		}

		String branchKey = switch (effectiveResponse) {
			case Neutral -> "default";
			case Option1 -> "option1";
			case Option2 -> "option2";
			case None    -> null;
		};

		if (branchKey == null) {
			close();
			return;
		}

		JSONObject currentObj = _file.getJSONObject(_currentDialogID);
		if (currentObj == null) {
			close();
			return;
		}

		JSONObject branch = currentObj.getJSONObject(branchKey);
		if (branch == null) {
			close();
			return;
		}

		if (!isBranchAllowed(branch)) {
			close();
			return;
		}

		String nextDialogID = null;
		String sceneToLoad = null;

		Iterator<?> keys = branch.keyIterator();
		while (keys.hasNext()) {
			String prop = keys.next().toString();
			String val = branch.getString(prop, "");

			switch (prop) {
				case "__define__" -> defineFlag(val);
				case "__playsfx__" -> {
					if (!val.isEmpty()) {
						String sfxPath = val.startsWith("resources/") ? val.substring("resources/".length()) : val;
						Sound sfx = ResourcesManager.loadSFX(sfxPath);
						Audio.playSFX(sfx);
					}
				}
				case "__give_item__" -> triggerGiveItem(val);
				case "__save_next__" -> {
					if (!_currentContextKey.isEmpty()) {
						_savedNextByContext.put(_currentContextKey, val);
					} else {
						_savedStartID = val;
					}
				}
				case "__save_next_lose__" -> {
					if (!_currentContextKey.isEmpty()) {
						_savedLoseNextByContext.put(_currentContextKey, val);
					} else {
						_savedLoseStartID = val;
					}
				}
				case "next" -> nextDialogID = val;
				case "__load__", "__loadr__", "__loads__" -> sceneToLoad = val;
			}
		}

		if (sceneToLoad != null && !sceneToLoad.isEmpty()) {
			_lastLoadedContextKey = _currentContextKey;
			close();
			if (onLoadRequest != null) {
				onLoadRequest.accept(sceneToLoad);
			}
			return;
		}

		if (nextDialogID != null && !nextDialogID.isEmpty()) {
			showNode(nextDialogID);
		} else {
			close();
		}
	}

	private static void triggerGiveItem(String rawVal) {
		if (rawVal == null || rawVal.isEmpty() || onGiveItemRequest == null) return;
		String itemId = rawVal.trim();
		int count = 1;
		int colonIdx = rawVal.indexOf(':');
		if (colonIdx > 0) {
			itemId = rawVal.substring(0, colonIdx).trim();
			try {
				count = Math.max(1, Integer.parseInt(rawVal.substring(colonIdx + 1).trim()));
			} catch (NumberFormatException ignored) {
				count = 1;
			}
		}
		if (!itemId.isEmpty()) {
			onGiveItemRequest.accept(itemId, count);
		}
	}
}
