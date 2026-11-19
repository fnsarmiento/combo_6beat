module scenes.map_editor.gui_widgets;

nothrow:
@nogc:

import raylib;
import raygui;
import core.stdc.stdio : snprintf;

public:

/**
 * Resetea el foco activo de edición de campos GUI.
 */
void gui_clear_active_field() {
    active_field_id = -1;
}

/**
 * Retorna true si algún campo de texto o numérico está actualmente en modo edición.
 */
bool gui_is_editing_field() {
    return active_field_id != -1;
}

/**
 * Dibuja un encabezado de sección dentro de un panel.
 */
void gui_section_header(float x, ref float y, float width, const(char)* title) {
    GuiLine(Rectangle(x, y, width, 18), title);
    y += 22;
}

/**
 * Campo de texto editable con etiqueta a la izquierda.
 */
bool gui_labeled_text_box(
    float x,
    ref float y,
    float width,
    const(char)* label,
    char* textBuffer,
    int bufferSize,
    int fieldId
) {
    float labelWidth = 95.0f;
    GuiLabel(Rectangle(x, y, labelWidth, ROW_HEIGHT), label);

    bool editMode = (active_field_id == fieldId);
    Rectangle boxRect = Rectangle(x + labelWidth, y, width - labelWidth, ROW_HEIGHT);
    if (GuiTextBox(boxRect, textBuffer, bufferSize, editMode)) {
        active_field_id = editMode ? -1 : fieldId;
        y += ROW_STEP;
        return true;
    }

    y += ROW_STEP;
    return false;
}

/**
 * Caja de texto simple (por ejemplo para barra buscadora) con manejo automático de foco.
 */
bool gui_search_box(Rectangle bounds, char* textBuffer, int bufferSize, int fieldId) {
    bool editMode = (active_field_id == fieldId);
    if (GuiTextBox(bounds, textBuffer, bufferSize, editMode)) {
        active_field_id = editMode ? -1 : fieldId;
        return true;
    }
    return false;
}

/**
 * Spinner entero con etiqueta a la izquierda.
 */
void gui_labeled_int_spinner(
    float x,
    ref float y,
    float width,
    const(char)* label,
    int* value,
    int minVal,
    int maxVal,
    int fieldId
) {
    float labelWidth = 105.0f;
    GuiLabel(Rectangle(x, y, labelWidth, ROW_HEIGHT), label);

    bool editMode = (active_field_id == fieldId);
    Rectangle spinnerRect = Rectangle(x + labelWidth, y, width - labelWidth, ROW_HEIGHT);
    if (GuiSpinner(spinnerRect, null, value, minVal, maxVal, editMode)) {
        active_field_id = editMode ? -1 : fieldId;
    }

    y += ROW_STEP;
}

/**
 * ValueBox entero con etiqueta a la izquierda.
 */
void gui_labeled_int_box(
    float x,
    ref float y,
    float width,
    const(char)* label,
    int* value,
    int minVal,
    int maxVal,
    int fieldId
) {
    float labelWidth = 105.0f;
    GuiLabel(Rectangle(x, y, labelWidth, ROW_HEIGHT), label);

    bool editMode = (active_field_id == fieldId);
    Rectangle boxRect = Rectangle(x + labelWidth, y, width - labelWidth, ROW_HEIGHT);
    if (GuiValueBox(boxRect, null, value, minVal, maxVal, editMode)) {
        active_field_id = editMode ? -1 : fieldId;
    }

    y += ROW_STEP;
}

/**
 * Campo float individual con manejo automático de buffer de texto.
 */
void gui_float_box(Rectangle bounds, const(char)* label, float* value, int fieldId) {
    if (active_field_id == fieldId) {
        if (GuiValueBoxFloat(bounds, label, active_float_buffer.ptr, value, true)) {
            active_field_id = -1;
        }
    } else {
        char[32] tempBuffer;
        snprintf(tempBuffer.ptr, tempBuffer.length, "%.2f", *value);
        if (GuiValueBoxFloat(bounds, label, tempBuffer.ptr, value, false)) {
            active_field_id = fieldId;
            snprintf(active_float_buffer.ptr, active_float_buffer.length, "%.2f", *value);
        }
    }
}

/**
 * Campo float con etiqueta a la izquierda.
 */
void gui_labeled_float(
    float x,
    ref float y,
    float width,
    const(char)* label,
    float* value,
    int fieldId
) {
    float labelWidth = 105.0f;
    GuiLabel(Rectangle(x, y, labelWidth, ROW_HEIGHT), label);
    Rectangle boxRect = Rectangle(x + labelWidth, y, width - labelWidth, ROW_HEIGHT);
    gui_float_box(boxRect, null, value, fieldId);
    y += ROW_STEP;
}

/**
 * Campo Vector2 (X, Y) en una sola fila con etiqueta a la izquierda.
 */
void gui_labeled_vec2(
    float x,
    ref float y,
    float width,
    const(char)* label,
    Vector2* vec,
    int baseFieldId
) {
    float labelWidth = 85.0f;
    GuiLabel(Rectangle(x, y, labelWidth, ROW_HEIGHT), label);

    float remaining = width - labelWidth;
    float subLabelW = 16.0f;
    float boxW = (remaining - subLabelW * 2.0f - 8.0f) * 0.5f;

    float curX = x + labelWidth;
    GuiLabel(Rectangle(curX, y, subLabelW, ROW_HEIGHT), "X");
    curX += subLabelW;
    gui_float_box(Rectangle(curX, y, boxW, ROW_HEIGHT), null, &vec.x, baseFieldId);

    curX += boxW + 8.0f;
    GuiLabel(Rectangle(curX, y, subLabelW, ROW_HEIGHT), "Y");
    curX += subLabelW;
    gui_float_box(Rectangle(curX, y, boxW, ROW_HEIGHT), null, &vec.y, baseFieldId + 1);

    y += ROW_STEP;
}

/**
 * Checkbox booleano con etiqueta.
 */
void gui_labeled_checkbox(float x, ref float y, const(char)* label, bool* value) {
    GuiCheckBox(Rectangle(x, y + 2, 20, 20), label, value);
    y += ROW_STEP;
}

/**
 * Selector de bitmap de 16 bits (2 filas x 8 bits) + valor numérico entero,
 * ideal para propiedades como layer y mask de colisión.
 */
void gui_labeled_bitmap(
    float x,
    ref float y,
    float width,
    const(char)* label,
    int* bitmapValue,
    int fieldId
) {
    // Cabecera con nombre y valor decimal directo
    float labelWidth = width - 90.0f;
    GuiLabel(Rectangle(x, y, labelWidth, 22), label);

    bool editMode = (active_field_id == fieldId);
    if (GuiValueBox(Rectangle(x + labelWidth, y, 90.0f, 22), null, bitmapValue, 0, 0xFFFF, editMode)) {
        active_field_id = editMode ? -1 : fieldId;
    }
    y += 25;

    // Grilla de 2 filas x 8 columnas para conmutar bits individuales (bits 0..15)
    int cols = 8;
    int rows = 2;
    float spacing = 2.0f;
    float btnW = (width - spacing * (cols - 1)) / cols;
    float btnH = 20.0f;

    for (int r = 0; r < rows; r++) {
        for (int c = 0; c < cols; c++) {
            int bitIndex = r * cols + c;
            int bitMask = 1 << bitIndex;
            bool isSet = ((*bitmapValue) & bitMask) != 0;

            char[8] bitLabel;
            snprintf(bitLabel.ptr, bitLabel.length, "%d", bitIndex + 1);

            Rectangle btnBounds = Rectangle(
                x + c * (btnW + spacing),
                y + r * (btnH + spacing),
                btnW,
                btnH
            );

            bool prevSet = isSet;
            GuiToggle(btnBounds, bitLabel.ptr, &isSet);
            if (isSet != prevSet) {
                if (isSet) {
                    *bitmapValue |= bitMask;
                } else {
                    *bitmapValue &= ~bitMask;
                }
            }
        }
    }

    y += rows * (btnH + spacing) + 6.0f;
}

/**
 * Botón rojo de eliminar / papelera (estilo destructivo).
 */
bool gui_trash_button(Rectangle bounds) {
    int prevBaseNormal = GuiGetStyle(BUTTON, BASE_COLOR_NORMAL);
    int prevBorderNormal = GuiGetStyle(BUTTON, BORDER_COLOR_NORMAL);
    int prevTextNormal = GuiGetStyle(BUTTON, TEXT_COLOR_NORMAL);
    int prevBaseFocused = GuiGetStyle(BUTTON, BASE_COLOR_FOCUSED);
    int prevTextFocused = GuiGetStyle(BUTTON, TEXT_COLOR_FOCUSED);

    GuiSetStyle(BUTTON, BASE_COLOR_NORMAL, 0x992222ff);
    GuiSetStyle(BUTTON, BORDER_COLOR_NORMAL, 0xcc4444ff);
    GuiSetStyle(BUTTON, TEXT_COLOR_NORMAL, 0xffffffff);
    GuiSetStyle(BUTTON, BASE_COLOR_FOCUSED, 0xcc3333ff);
    GuiSetStyle(BUTTON, TEXT_COLOR_FOCUSED, 0xffffffff);

    bool clicked = GuiButton(bounds, GuiIconText(GuiIconName.ICON_BIN, null)) != 0;

    GuiSetStyle(BUTTON, BASE_COLOR_NORMAL, prevBaseNormal);
    GuiSetStyle(BUTTON, BORDER_COLOR_NORMAL, prevBorderNormal);
    GuiSetStyle(BUTTON, TEXT_COLOR_NORMAL, prevTextNormal);
    GuiSetStyle(BUTTON, BASE_COLOR_FOCUSED, prevBaseFocused);
    GuiSetStyle(BUTTON, TEXT_COLOR_FOCUSED, prevTextFocused);

    return clicked;
}

private:

enum float ROW_HEIGHT = 24.0f;
enum float ROW_STEP = 30.0f;

__gshared int active_field_id = -1;
__gshared char[32] active_float_buffer = 0;
