module raygui;

import raylib : Rectangle, Vector2, Vector3, Color, Texture2D, Font;

extern (C) @nogc nothrow:

//----------------------------------------------------------------------------------
// Defines and Macros
//----------------------------------------------------------------------------------
enum RAYGUI_VERSION_MAJOR = 5;
enum RAYGUI_VERSION_MINOR = 1;
enum RAYGUI_VERSION_PATCH = 0;
enum RAYGUI_VERSION = "5.1-dev";

enum SCROLLBAR_LEFT_SIDE = 0;
enum SCROLLBAR_RIGHT_SIDE = 1;

//----------------------------------------------------------------------------------
// Types and Structures Definition
//----------------------------------------------------------------------------------

// Style property
struct GuiStyleProp {
    ushort controlId;   // Control identifier
    ushort propertyId;  // Property identifier
    int propertyValue;  // Property value
}

// Gui control result
enum GuiResult {
    RESULT_NONE      = 0,
    RESULT_PRESSED   = 1,
    RESULT_CHANGED   = 2,
    RESULT_TAB_CLOSE = 4
}
alias RESULT_NONE      = GuiResult.RESULT_NONE;
alias RESULT_PRESSED   = GuiResult.RESULT_PRESSED;
alias RESULT_CHANGED   = GuiResult.RESULT_CHANGED;
alias RESULT_TAB_CLOSE = GuiResult.RESULT_TAB_CLOSE;

// Gui control state
enum GuiState {
    STATE_NORMAL = 0,
    STATE_FOCUSED,
    STATE_PRESSED,
    STATE_DISABLED
}
alias STATE_NORMAL   = GuiState.STATE_NORMAL;
alias STATE_FOCUSED  = GuiState.STATE_FOCUSED;
alias STATE_PRESSED  = GuiState.STATE_PRESSED;
alias STATE_DISABLED = GuiState.STATE_DISABLED;

// Gui control text alignment
enum GuiTextAlignment {
    TEXT_ALIGN_LEFT = 0,
    TEXT_ALIGN_CENTER,
    TEXT_ALIGN_RIGHT
}
alias TEXT_ALIGN_LEFT   = GuiTextAlignment.TEXT_ALIGN_LEFT;
alias TEXT_ALIGN_CENTER = GuiTextAlignment.TEXT_ALIGN_CENTER;
alias TEXT_ALIGN_RIGHT  = GuiTextAlignment.TEXT_ALIGN_RIGHT;

// Gui control text alignment vertical
enum GuiTextAlignmentVertical {
    TEXT_ALIGN_TOP = 0,
    TEXT_ALIGN_MIDDLE,
    TEXT_ALIGN_BOTTOM
}
alias TEXT_ALIGN_TOP    = GuiTextAlignmentVertical.TEXT_ALIGN_TOP;
alias TEXT_ALIGN_MIDDLE = GuiTextAlignmentVertical.TEXT_ALIGN_MIDDLE;
alias TEXT_ALIGN_BOTTOM = GuiTextAlignmentVertical.TEXT_ALIGN_BOTTOM;

// Gui control text wrap mode
enum GuiTextWrapMode {
    TEXT_WRAP_NONE = 0,
    TEXT_WRAP_CHAR,
    TEXT_WRAP_WORD
}
alias TEXT_WRAP_NONE = GuiTextWrapMode.TEXT_WRAP_NONE;
alias TEXT_WRAP_CHAR = GuiTextWrapMode.TEXT_WRAP_CHAR;
alias TEXT_WRAP_WORD = GuiTextWrapMode.TEXT_WRAP_WORD;

// Gui controls
enum GuiControl {
    DEFAULT     = 0,
    LABEL       = 1,
    BUTTON      = 2,
    TOGGLE      = 3,
    SLIDER      = 4,
    PROGRESSBAR = 5,
    CHECKBOX    = 6,
    COMBOBOX    = 7,
    DROPDOWNBOX = 8,
    TEXTBOX     = 9,
    VALUEBOX    = 10,
    TABBAR      = 11,
    LISTVIEW    = 12,
    COLORPICKER = 13,
    SCROLLBAR   = 14,
    STATUSBAR   = 15
}
alias DEFAULT     = GuiControl.DEFAULT;
alias LABEL       = GuiControl.LABEL;
alias BUTTON      = GuiControl.BUTTON;
alias TOGGLE      = GuiControl.TOGGLE;
alias SLIDER      = GuiControl.SLIDER;
alias PROGRESSBAR = GuiControl.PROGRESSBAR;
alias CHECKBOX    = GuiControl.CHECKBOX;
alias COMBOBOX    = GuiControl.COMBOBOX;
alias DROPDOWNBOX = GuiControl.DROPDOWNBOX;
alias TEXTBOX     = GuiControl.TEXTBOX;
alias VALUEBOX    = GuiControl.VALUEBOX;
alias TABBAR      = GuiControl.TABBAR;
alias LISTVIEW    = GuiControl.LISTVIEW;
alias COLORPICKER = GuiControl.COLORPICKER;
alias SCROLLBAR   = GuiControl.SCROLLBAR;
alias STATUSBAR   = GuiControl.STATUSBAR;

// Controls BASE properties for every control
enum GuiControlProperty {
    BORDER_COLOR_NORMAL   = 0,
    BASE_COLOR_NORMAL     = 1,
    TEXT_COLOR_NORMAL     = 2,
    BORDER_COLOR_FOCUSED  = 3,
    BASE_COLOR_FOCUSED    = 4,
    TEXT_COLOR_FOCUSED    = 5,
    BORDER_COLOR_PRESSED  = 6,
    BASE_COLOR_PRESSED    = 7,
    TEXT_COLOR_PRESSED    = 8,
    BORDER_COLOR_DISABLED = 9,
    BASE_COLOR_DISABLED   = 10,
    TEXT_COLOR_DISABLED   = 11,
    BORDER_WIDTH          = 12,
    TEXT_PADDING          = 13,
    TEXT_ALIGNMENT        = 14,
    BASEPROP16            = 15
}
alias BORDER_COLOR_NORMAL   = GuiControlProperty.BORDER_COLOR_NORMAL;
alias BASE_COLOR_NORMAL     = GuiControlProperty.BASE_COLOR_NORMAL;
alias TEXT_COLOR_NORMAL     = GuiControlProperty.TEXT_COLOR_NORMAL;
alias BORDER_COLOR_FOCUSED  = GuiControlProperty.BORDER_COLOR_FOCUSED;
alias BASE_COLOR_FOCUSED    = GuiControlProperty.BASE_COLOR_FOCUSED;
alias TEXT_COLOR_FOCUSED    = GuiControlProperty.TEXT_COLOR_FOCUSED;
alias BORDER_COLOR_PRESSED  = GuiControlProperty.BORDER_COLOR_PRESSED;
alias BASE_COLOR_PRESSED    = GuiControlProperty.BASE_COLOR_PRESSED;
alias TEXT_COLOR_PRESSED    = GuiControlProperty.TEXT_COLOR_PRESSED;
alias BORDER_COLOR_DISABLED = GuiControlProperty.BORDER_COLOR_DISABLED;
alias BASE_COLOR_DISABLED   = GuiControlProperty.BASE_COLOR_DISABLED;
alias TEXT_COLOR_DISABLED   = GuiControlProperty.TEXT_COLOR_DISABLED;
alias BORDER_WIDTH          = GuiControlProperty.BORDER_WIDTH;
alias TEXT_PADDING          = GuiControlProperty.TEXT_PADDING;
alias TEXT_ALIGNMENT        = GuiControlProperty.TEXT_ALIGNMENT;
alias BASEPROP16            = GuiControlProperty.BASEPROP16;

// DEFAULT control, extended properties
enum GuiDefaultProperty {
    TEXT_SIZE               = 16,
    TEXT_SPACING            = 17,
    LINE_COLOR              = 18,
    BACKGROUND_COLOR        = 19,
    TEXT_LINE_SPACING       = 20,
    TEXT_ALIGNMENT_VERTICAL = 21,
    TEXT_WRAP_MODE          = 22,
    EXTPROP08               = 23
}
alias TEXT_SIZE               = GuiDefaultProperty.TEXT_SIZE;
alias TEXT_SPACING            = GuiDefaultProperty.TEXT_SPACING;
alias LINE_COLOR              = GuiDefaultProperty.LINE_COLOR;
alias BACKGROUND_COLOR        = GuiDefaultProperty.BACKGROUND_COLOR;
alias TEXT_LINE_SPACING       = GuiDefaultProperty.TEXT_LINE_SPACING;
alias TEXT_ALIGNMENT_VERTICAL = GuiDefaultProperty.TEXT_ALIGNMENT_VERTICAL;
alias TEXT_WRAP_MODE          = GuiDefaultProperty.TEXT_WRAP_MODE;
alias EXTPROP08               = GuiDefaultProperty.EXTPROP08;

// Toggle/ToggleGroup
enum GuiToggleProperty {
    GROUP_PADDING = 16,
    GROUP_WIDTH_FULL
}
alias GROUP_PADDING    = GuiToggleProperty.GROUP_PADDING;
alias GROUP_WIDTH_FULL = GuiToggleProperty.GROUP_WIDTH_FULL;

// Slider/SliderBar
enum GuiSliderProperty {
    SLIDER_WIDTH = 16,
    SLIDER_PADDING
}
alias SLIDER_WIDTH   = GuiSliderProperty.SLIDER_WIDTH;
alias SLIDER_PADDING = GuiSliderProperty.SLIDER_PADDING;

// ProgressBar
enum GuiProgressBarProperty {
    PROGRESS_PADDING = 16,
    PROGRESS_SIDE
}
alias PROGRESS_PADDING = GuiProgressBarProperty.PROGRESS_PADDING;
alias PROGRESS_SIDE    = GuiProgressBarProperty.PROGRESS_SIDE;

// ScrollBar
enum GuiScrollBarProperty {
    ARROWS_SIZE = 16,
    ARROWS_VISIBLE,
    SCROLL_SLIDER_PADDING,
    SCROLL_SLIDER_SIZE,
    SCROLL_PADDING,
    SCROLL_SPEED
}
alias ARROWS_SIZE           = GuiScrollBarProperty.ARROWS_SIZE;
alias ARROWS_VISIBLE        = GuiScrollBarProperty.ARROWS_VISIBLE;
alias SCROLL_SLIDER_PADDING = GuiScrollBarProperty.SCROLL_SLIDER_PADDING;
alias SCROLL_SLIDER_SIZE    = GuiScrollBarProperty.SCROLL_SLIDER_SIZE;
alias SCROLL_PADDING        = GuiScrollBarProperty.SCROLL_PADDING;
alias SCROLL_SPEED          = GuiScrollBarProperty.SCROLL_SPEED;

// CheckBox
enum GuiCheckBoxProperty {
    CHECK_PADDING = 16
}
alias CHECK_PADDING = GuiCheckBoxProperty.CHECK_PADDING;

// ComboBox
enum GuiComboBoxProperty {
    COMBO_BUTTON_WIDTH = 16,
    COMBO_BUTTON_SPACING
}
alias COMBO_BUTTON_WIDTH   = GuiComboBoxProperty.COMBO_BUTTON_WIDTH;
alias COMBO_BUTTON_SPACING = GuiComboBoxProperty.COMBO_BUTTON_SPACING;

// DropdownBox
enum GuiDropdownBoxProperty {
    ARROW_PADDING = 16,
    DROPDOWN_ITEMS_SPACING,
    DROPDOWN_ARROW_HIDDEN,
    DROPDOWN_ROLL_UP,
    DROPDOWN_MAX_HEIGHT
}
alias ARROW_PADDING          = GuiDropdownBoxProperty.ARROW_PADDING;
alias DROPDOWN_ITEMS_SPACING = GuiDropdownBoxProperty.DROPDOWN_ITEMS_SPACING;
alias DROPDOWN_ARROW_HIDDEN  = GuiDropdownBoxProperty.DROPDOWN_ARROW_HIDDEN;
alias DROPDOWN_ROLL_UP       = GuiDropdownBoxProperty.DROPDOWN_ROLL_UP;
alias DROPDOWN_MAX_HEIGHT    = GuiDropdownBoxProperty.DROPDOWN_MAX_HEIGHT;

// TextBox/TextBoxMulti/ValueBox/Spinner
enum GuiTextBoxProperty {
    TEXT_READONLY = 16
}
alias TEXT_READONLY = GuiTextBoxProperty.TEXT_READONLY;

// ValueBox/Spinner
enum GuiValueBoxProperty {
    SPINNER_BUTTON_WIDTH = 16,
    SPINNER_BUTTON_SPACING
}
alias SPINNER_BUTTON_WIDTH   = GuiValueBoxProperty.SPINNER_BUTTON_WIDTH;
alias SPINNER_BUTTON_SPACING = GuiValueBoxProperty.SPINNER_BUTTON_SPACING;

// TabBar
enum GuiTabBarProperty {
    TAB_ITEMS_WIDTH = 16,
    TAB_CLOSE_BUTTON,
    TAB_LINE_SIDE
}
alias TAB_ITEMS_WIDTH  = GuiTabBarProperty.TAB_ITEMS_WIDTH;
alias TAB_CLOSE_BUTTON = GuiTabBarProperty.TAB_CLOSE_BUTTON;
alias TAB_LINE_SIDE    = GuiTabBarProperty.TAB_LINE_SIDE;

// ListView
enum GuiListViewProperty {
    LIST_ITEMS_HEIGHT = 16,
    LIST_ITEMS_SPACING,
    SCROLLBAR_WIDTH,
    SCROLLBAR_SIDE,
    LIST_ITEMS_BORDER_NORMAL,
    LIST_ITEMS_BORDER_WIDTH
}
alias LIST_ITEMS_HEIGHT        = GuiListViewProperty.LIST_ITEMS_HEIGHT;
alias LIST_ITEMS_SPACING       = GuiListViewProperty.LIST_ITEMS_SPACING;
alias SCROLLBAR_WIDTH          = GuiListViewProperty.SCROLLBAR_WIDTH;
alias SCROLLBAR_SIDE           = GuiListViewProperty.SCROLLBAR_SIDE;
alias LIST_ITEMS_BORDER_NORMAL = GuiListViewProperty.LIST_ITEMS_BORDER_NORMAL;
alias LIST_ITEMS_BORDER_WIDTH  = GuiListViewProperty.LIST_ITEMS_BORDER_WIDTH;

// ColorPicker
enum GuiColorPickerProperty {
    COLOR_SELECTOR_SIZE = 16,
    HUEBAR_WIDTH,
    HUEBAR_PADDING,
    HUEBAR_SELECTOR_HEIGHT,
    HUEBAR_SELECTOR_OVERFLOW
}
alias COLOR_SELECTOR_SIZE      = GuiColorPickerProperty.COLOR_SELECTOR_SIZE;
alias HUEBAR_WIDTH             = GuiColorPickerProperty.HUEBAR_WIDTH;
alias HUEBAR_PADDING           = GuiColorPickerProperty.HUEBAR_PADDING;
alias HUEBAR_SELECTOR_HEIGHT   = GuiColorPickerProperty.HUEBAR_SELECTOR_HEIGHT;
alias HUEBAR_SELECTOR_OVERFLOW = GuiColorPickerProperty.HUEBAR_SELECTOR_OVERFLOW;

// Icons enumeration
enum GuiIconName {
    ICON_NONE                     = 0,
    ICON_FOLDER_FILE_OPEN         = 1,
    ICON_FILE_SAVE_CLASSIC        = 2,
    ICON_FOLDER_OPEN              = 3,
    ICON_FOLDER_SAVE              = 4,
    ICON_FILE_OPEN                = 5,
    ICON_FILE_SAVE                = 6,
    ICON_FILE_EXPORT              = 7,
    ICON_FILE_ADD                 = 8,
    ICON_FILE_DELETE              = 9,
    ICON_FILETYPE_TEXT            = 10,
    ICON_FILETYPE_AUDIO           = 11,
    ICON_FILETYPE_IMAGE           = 12,
    ICON_FILETYPE_PLAY            = 13,
    ICON_FILETYPE_VIDEO           = 14,
    ICON_FILETYPE_INFO            = 15,
    ICON_FILE_COPY                = 16,
    ICON_FILE_CUT                 = 17,
    ICON_FILE_PASTE               = 18,
    ICON_CURSOR_HAND              = 19,
    ICON_CURSOR_POINTER           = 20,
    ICON_CURSOR_CLASSIC           = 21,
    ICON_PENCIL                   = 22,
    ICON_PENCIL_BIG               = 23,
    ICON_BRUSH_CLASSIC            = 24,
    ICON_BRUSH_PAINTER            = 25,
    ICON_WATER_DROP               = 26,
    ICON_COLOR_PICKER             = 27,
    ICON_RUBBER                   = 28,
    ICON_COLOR_BUCKET             = 29,
    ICON_TEXT_T                   = 30,
    ICON_TEXT_A                   = 31,
    ICON_SCALE                    = 32,
    ICON_RESIZE                   = 33,
    ICON_FILTER_POINT             = 34,
    ICON_FILTER_BILINEAR          = 35,
    ICON_CROP                     = 36,
    ICON_CROP_ALPHA               = 37,
    ICON_SQUARE_TOGGLE            = 38,
    ICON_SYMMETRY                 = 39,
    ICON_SYMMETRY_HORIZONTAL      = 40,
    ICON_SYMMETRY_VERTICAL        = 41,
    ICON_LENS                     = 42,
    ICON_LENS_BIG                 = 43,
    ICON_EYE_ON                   = 44,
    ICON_EYE_OFF                  = 45,
    ICON_FILTER_TOP               = 46,
    ICON_FILTER                   = 47,
    ICON_TARGET_POINT             = 48,
    ICON_TARGET_SMALL             = 49,
    ICON_TARGET_BIG               = 50,
    ICON_TARGET_MOVE              = 51,
    ICON_CURSOR_MOVE              = 52,
    ICON_CURSOR_SCALE             = 53,
    ICON_CURSOR_SCALE_RIGHT       = 54,
    ICON_CURSOR_SCALE_LEFT        = 55,
    ICON_UNDO                     = 56,
    ICON_REDO                     = 57,
    ICON_REREDO                   = 58,
    ICON_MUTATE                   = 59,
    ICON_ROTATE                   = 60,
    ICON_REPEAT                   = 61,
    ICON_SHUFFLE                  = 62,
    ICON_EMPTYBOX                 = 63,
    ICON_TARGET                   = 64,
    ICON_TARGET_SMALL_FILL        = 65,
    ICON_TARGET_BIG_FILL          = 66,
    ICON_TARGET_MOVE_FILL         = 67,
    ICON_CURSOR_MOVE_FILL         = 68,
    ICON_CURSOR_SCALE_FILL        = 69,
    ICON_CURSOR_SCALE_RIGHT_FILL  = 70,
    ICON_CURSOR_SCALE_LEFT_FILL   = 71,
    ICON_UNDO_FILL                = 72,
    ICON_REDO_FILL                = 73,
    ICON_REREDO_FILL              = 74,
    ICON_MUTATE_FILL              = 75,
    ICON_ROTATE_FILL              = 76,
    ICON_REPEAT_FILL              = 77,
    ICON_SHUFFLE_FILL             = 78,
    ICON_EMPTYBOX_SMALL           = 79,
    ICON_BOX                      = 80,
    ICON_BOX_TOP                  = 81,
    ICON_BOX_TOP_RIGHT            = 82,
    ICON_BOX_RIGHT                = 83,
    ICON_BOX_BOTTOM_RIGHT         = 84,
    ICON_BOX_BOTTOM               = 85,
    ICON_BOX_BOTTOM_LEFT          = 86,
    ICON_BOX_LEFT                 = 87,
    ICON_BOX_TOP_LEFT             = 88,
    ICON_BOX_CENTER               = 89,
    ICON_BOX_CIRCLE_MASK          = 90,
    ICON_POT                      = 91,
    ICON_ALPHA_MULTIPLY           = 92,
    ICON_ALPHA_CLEAR              = 93,
    ICON_DITHERING                = 94,
    ICON_MIPMAPS                  = 95,
    ICON_BOX_GRID                 = 96,
    ICON_GRID                     = 97,
    ICON_BOX_CORNERS_SMALL        = 98,
    ICON_BOX_CORNERS_BIG          = 99,
    ICON_FOUR_BOXES               = 100,
    ICON_GRID_FILL                = 101,
    ICON_BOX_MULTISIZE            = 102,
    ICON_ZOOM_SMALL               = 103,
    ICON_ZOOM_MEDIUM              = 104,
    ICON_ZOOM_BIG                 = 105,
    ICON_ZOOM_ALL                 = 106,
    ICON_ZOOM_CENTER              = 107,
    ICON_BOX_DOTS_SMALL           = 108,
    ICON_BOX_DOTS_BIG             = 109,
    ICON_BOX_CONCENTRIC           = 110,
    ICON_BOX_GRID_BIG             = 111,
    ICON_OK_TICK                  = 112,
    ICON_CROSS                    = 113,
    ICON_ARROW_LEFT               = 114,
    ICON_ARROW_RIGHT              = 115,
    ICON_ARROW_DOWN               = 116,
    ICON_ARROW_UP                 = 117,
    ICON_ARROW_LEFT_FILL          = 118,
    ICON_ARROW_RIGHT_FILL         = 119,
    ICON_ARROW_DOWN_FILL          = 120,
    ICON_ARROW_UP_FILL            = 121,
    ICON_AUDIO                    = 122,
    ICON_FX                       = 123,
    ICON_WAVE                     = 124,
    ICON_WAVE_SINUS               = 125,
    ICON_WAVE_SQUARE              = 126,
    ICON_WAVE_TRIANGULAR          = 127,
    ICON_CROSS_SMALL              = 128,
    ICON_PLAYER_PREVIOUS          = 129,
    ICON_PLAYER_PLAY_BACK         = 130,
    ICON_PLAYER_PLAY              = 131,
    ICON_PLAYER_PAUSE             = 132,
    ICON_PLAYER_STOP              = 133,
    ICON_PLAYER_NEXT              = 134,
    ICON_PLAYER_RECORD            = 135,
    ICON_MAGNET                   = 136,
    ICON_LOCK_CLOSE               = 137,
    ICON_LOCK_OPEN                = 138,
    ICON_CLOCK                    = 139,
    ICON_TOOLS                    = 140,
    ICON_GEAR                     = 141,
    ICON_GEAR_BIG                 = 142,
    ICON_BIN                      = 143,
    ICON_HAND_POINTER             = 144,
    ICON_LASER                    = 145,
    ICON_COIN                     = 146,
    ICON_EXPLOSION                = 147,
    ICON_1UP                      = 148,
    ICON_PLAYER                   = 149,
    ICON_PLAYER_JUMP              = 150,
    ICON_KEY                      = 151,
    ICON_DEMON                    = 152,
    ICON_TEXT_POPUP               = 153,
    ICON_GEAR_EX                  = 154,
    ICON_CRACK                    = 155,
    ICON_CRACK_POINTS             = 156,
    ICON_STAR                     = 157,
    ICON_DOOR                     = 158,
    ICON_EXIT                     = 159,
    ICON_MODE_2D                  = 160,
    ICON_MODE_3D                  = 161,
    ICON_CUBE                     = 162,
    ICON_CUBE_FACE_TOP            = 163,
    ICON_CUBE_FACE_LEFT           = 164,
    ICON_CUBE_FACE_FRONT          = 165,
    ICON_CUBE_FACE_BOTTOM         = 166,
    ICON_CUBE_FACE_RIGHT          = 167,
    ICON_CUBE_FACE_BACK           = 168,
    ICON_CAMERA                   = 169,
    ICON_SPECIAL                  = 170,
    ICON_LINK_NET                 = 171,
    ICON_LINK_BOXES               = 172,
    ICON_LINK_MULTI               = 173,
    ICON_LINK                     = 174,
    ICON_LINK_BROKE               = 175,
    ICON_TEXT_NOTES               = 176,
    ICON_NOTEBOOK                 = 177,
    ICON_SUITCASE                 = 178,
    ICON_SUITCASE_ZIP             = 179,
    ICON_MAILBOX                  = 180,
    ICON_MONITOR                  = 181,
    ICON_PRINTER                  = 182,
    ICON_PHOTO_CAMERA             = 183,
    ICON_PHOTO_CAMERA_FLASH       = 184,
    ICON_HOUSE                    = 185,
    ICON_HEART                    = 186,
    ICON_CORNER                   = 187,
    ICON_VERTICAL_BARS            = 188,
    ICON_VERTICAL_BARS_FILL       = 189,
    ICON_LIFE_BARS                = 190,
    ICON_INFO                     = 191,
    ICON_CROSSLINE                = 192,
    ICON_HELP                     = 193,
    ICON_FILETYPE_ALPHA           = 194,
    ICON_FILETYPE_HOME            = 195,
    ICON_LAYERS_VISIBLE           = 196,
    ICON_LAYERS                   = 197,
    ICON_WINDOW                   = 198,
    ICON_HIDPI                    = 199,
    ICON_FILETYPE_BINARY          = 200,
    ICON_HEX                      = 201,
    ICON_SHIELD                   = 202,
    ICON_FILE_NEW                 = 203,
    ICON_FOLDER_ADD               = 204,
    ICON_ALARM                    = 205,
    ICON_CPU                      = 206,
    ICON_ROM                      = 207,
    ICON_STEP_OVER                = 208,
    ICON_STEP_INTO                = 209,
    ICON_STEP_OUT                 = 210,
    ICON_RESTART                  = 211,
    ICON_BREAKPOINT_ON            = 212,
    ICON_BREAKPOINT_OFF           = 213,
    ICON_BURGER_MENU              = 214,
    ICON_CASE_SENSITIVE           = 215,
    ICON_REG_EXP                  = 216,
    ICON_FOLDER                   = 217,
    ICON_FILE                     = 218,
    ICON_SAND_TIMER               = 219,
    ICON_WARNING                  = 220,
    ICON_HELP_BOX                 = 221,
    ICON_INFO_BOX                 = 222,
    ICON_PRIORITY                 = 223,
    ICON_LAYERS_ISO               = 224,
    ICON_LAYERS2                  = 225,
    ICON_MLAYERS                  = 226,
    ICON_MAPS                     = 227,
    ICON_HOT                      = 228,
    ICON_LABEL                    = 229,
    ICON_NAME_ID                  = 230,
    ICON_SLICING                  = 231,
    ICON_MANUAL_CONTROL           = 232,
    ICON_COLLISION                = 233,
    ICON_CIRCLE_ADD               = 234,
    ICON_CIRCLE_ADD_FILL          = 235,
    ICON_CIRCLE_WARNING           = 236,
    ICON_CIRCLE_WARNING_FILL      = 237,
    ICON_BOX_MORE                 = 238,
    ICON_BOX_MORE_FILL            = 239,
    ICON_BOX_MINUS                = 240,
    ICON_BOX_MINUS_FILL           = 241,
    ICON_UNION                    = 242,
    ICON_INTERSECTION             = 243,
    ICON_DIFFERENCE               = 244,
    ICON_SPHERE                   = 245,
    ICON_CYLINDER                 = 246,
    ICON_CONE                     = 247,
    ICON_ELLIPSOID                = 248,
    ICON_CAPSULE                  = 249,
    ICON_FILETYPE_FONT            = 250,
    ICON_FILETYPE_3D              = 251,
    ICON_FILETYPE_CODE_XML        = 252,
    ICON_FILETYPE_CODE_C          = 253,
    ICON_FILETYPE_CODE_PYTHON     = 254,
    ICON_FILETYPE_CODE_JS         = 255,
    ICON_FILETYPE_ICON            = 256
}

//----------------------------------------------------------------------------------
// Module Functions Declaration
//----------------------------------------------------------------------------------

// Global gui state control functions
void GuiEnable();
void GuiDisable();
void GuiLock();
void GuiUnlock();
bool GuiIsLocked();
void GuiSetAlpha(float alpha);
void GuiSetState(int state);
int GuiGetState();

// Font set/get functions
void GuiSetFont(Font font);
Font GuiGetFont();

// Style set/get functions
void GuiSetStyle(int control, int property, int value);
int GuiGetStyle(int control, int property);

// Styles loading functions
void GuiLoadStyle(const(char)* fileName);
void GuiLoadStyleFromMemory(const(ubyte)* fileData, int dataSize);
void GuiLoadStyleDefault();

// Tooltips management functions
void GuiEnableTooltip();
void GuiDisableTooltip();
void GuiSetTooltip(const(char)* tooltip);

// Icons functionality
const(char)* GuiIconText(int iconId, const(char)* text);
void GuiSetIconScale(int scale);
uint* GuiGetIcons();
char** GuiLoadIcons(const(char)* fileName, bool loadIconsName);
char** GuiLoadIconsFromMemory(const(ubyte)* fileData, int dataSize, bool loadIconsName);
void GuiDrawIcon(int iconId, int posX, int posY, int pixelSize, Color color);

// Utility functions
int GuiGetTextWidth(const(char)* text);

// Container/separator controls, useful for controls organization
int GuiWindowBox(Rectangle bounds, const(char)* title);
int GuiGroupBox(Rectangle bounds, const(char)* text);
int GuiLine(Rectangle bounds, const(char)* text);
int GuiPanel(Rectangle bounds, const(char)* text);
int GuiScrollPanel(Rectangle bounds, const(char)* text, Rectangle content,
        Vector2* scroll, Rectangle* view);

// Basic controls set
int GuiLabel(Rectangle bounds, const(char)* text);
int GuiButton(Rectangle bounds, const(char)* text);
int GuiLabelButton(Rectangle bounds, const(char)* text);
int GuiToggle(Rectangle bounds, const(char)* text, bool* active);
int GuiToggleGroup(Rectangle bounds, const(char)* text, int* active);
int GuiToggleSlider(Rectangle bounds, const(char)* text, int* active);
int GuiCheckBox(Rectangle bounds, const(char)* text, bool* checked);
int GuiComboBox(Rectangle bounds, const(char)* text, int* active);

int GuiDropdownBox(Rectangle bounds, const(char)* text, int* active, bool editMode);
int GuiSpinner(Rectangle bounds, const(char)* text, int* value, int minValue,
        int maxValue, bool editMode);
int GuiValueBox(Rectangle bounds, const(char)* text, int* value, int minValue,
        int maxValue, bool editMode);
int GuiValueBoxFloat(Rectangle bounds, const(char)* text, char* textValue,
        float* value, bool editMode);
int GuiTextBox(Rectangle bounds, char* text, int textSize, bool editMode);

int GuiSlider(Rectangle bounds, const(char)* textLeft, const(char)* textRight,
        float* value, float minValue, float maxValue);
int GuiSliderBar(Rectangle bounds, const(char)* textLeft,
        const(char)* textRight, float* value, float minValue, float maxValue);
int GuiProgressBar(Rectangle bounds, const(char)* textLeft,
        const(char)* textRight, float* value, float minValue, float maxValue);
int GuiStatusBar(Rectangle bounds, const(char)* text);
int GuiDummyRec(Rectangle bounds, const(char)* text);
int GuiGrid(Rectangle bounds, const(char)* text, float spacing, int subdivs,
        Vector2* mouseCell);

// Advance controls set
int GuiListView(Rectangle bounds, const(char)* text, int* scrollIndex, int* active);
int GuiListViewEx(Rectangle bounds, char** text, int count, int* scrollIndex,
        int* active, int* focus);
int GuiTabBar(Rectangle bounds, const(char)* text, int* hscroll, int* active);
int GuiTabBarEx(Rectangle bounds, char** text, int count, int* hscroll,
        int* active, int* focus);
int GuiMessageBox(Rectangle bounds, const(char)* title, const(char)* message,
        const(char)* btnText, int* btnActive);
int GuiTextInputBox(Rectangle bounds, const(char)* title, const(char)* message,
        char* text, int textSize, const(char)* btnText, int* btnActive,
        bool* secretViewActive);
int GuiColorPicker(Rectangle bounds, const(char)* text, Color* color);
int GuiColorPanel(Rectangle bounds, const(char)* text, Color* color);
int GuiColorBarAlpha(Rectangle bounds, const(char)* text, float* alpha);
int GuiColorBarHue(Rectangle bounds, const(char)* text, float* value);
int GuiColorPickerHSV(Rectangle bounds, const(char)* text, Vector3* colorHsv);
int GuiColorPanelHSV(Rectangle bounds, const(char)* text, Vector3* colorHsv);

