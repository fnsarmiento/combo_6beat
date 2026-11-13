module scenes.map_editor.camera_controller;

nothrow:
@nogc:

import raylib;

public:

struct EditorCamera {
    Camera2D camera;
    bool initialized = false;
}

/**
 * Inicializa o resetea la cámara 2D del Level Editor centrada en (0, 0).
 */
void editor_camera_reset(ref EditorCamera edCam, Rectangle viewportBounds) {
    edCam.camera.target   = Vector2(0.0f, 0.0f);
    edCam.camera.offset   = Vector2(
        viewportBounds.x + viewportBounds.width * 0.25f,
        viewportBounds.y + viewportBounds.height * 0.25f
    );
    edCam.camera.rotation = 0.0f;
    edCam.camera.zoom     = 1.0f;
    edCam.initialized     = true;
}

/**
 * Actualiza el controlador de cámara (Pan con click central/derecho o Espacio+Click, Zoom con rueda del mouse).
 */
void editor_camera_update(ref EditorCamera edCam, Rectangle viewportBounds) {
    if (!edCam.initialized) {
        editor_camera_reset(edCam, viewportBounds);
    }

    Vector2 mousePos = GetMousePosition();
    if (!CheckCollisionPointRec(mousePos, viewportBounds)) {
        return;
    }

    // Panning con botón derecho, botón central o Espacio + botón izquierdo
    bool isPanning = IsMouseButtonDown(MouseButton.MOUSE_BUTTON_RIGHT) ||
                     IsMouseButtonDown(MouseButton.MOUSE_BUTTON_MIDDLE) ||
                     (IsKeyDown(KeyboardKey.KEY_SPACE) && IsMouseButtonDown(MouseButton.MOUSE_BUTTON_LEFT));

    if (isPanning) {
        Vector2 delta = GetMouseDelta();
        edCam.camera.target.x -= delta.x / edCam.camera.zoom;
        edCam.camera.target.y -= delta.y / edCam.camera.zoom;
    }

    // Zoom hacia el cursor con la rueda del mouse
    float wheel = GetMouseWheelMove();
    if (wheel != 0.0f) {
        Vector2 mouseWorldPos = GetScreenToWorld2D(mousePos, edCam.camera);
        edCam.camera.offset = mousePos;
        edCam.camera.target = mouseWorldPos;

        float zoomFactor = 1.0f + (wheel * 0.12f);
        if (zoomFactor < 0.2f) zoomFactor = 0.2f;

        edCam.camera.zoom *= zoomFactor;
        if (edCam.camera.zoom < MIN_ZOOM) edCam.camera.zoom = MIN_ZOOM;
        if (edCam.camera.zoom > MAX_ZOOM) edCam.camera.zoom = MAX_ZOOM;
    }
}

/**
 * Convierte la posición actual del mouse en pantalla a coordenadas del mundo 2D.
 */
Vector2 editor_camera_screen_to_world(ref const(EditorCamera) edCam, Vector2 screenPos) {
    return GetScreenToWorld2D(screenPos, edCam.camera);
}

/**
 * Dibuja la grilla infinita del mundo y los ejes de origen (0, 0) dentro de BeginMode2D.
 */
void editor_camera_draw_world_grid(ref const(EditorCamera) edCam, Rectangle viewportBounds) {
    Vector2 topLeft = GetScreenToWorld2D(
        Vector2(viewportBounds.x, viewportBounds.y),
        edCam.camera
    );
    Vector2 bottomRight = GetScreenToWorld2D(
        Vector2(viewportBounds.x + viewportBounds.width, viewportBounds.y + viewportBounds.height),
        edCam.camera
    );

    float gridStep = 32.0f;
    if (edCam.camera.zoom < 0.4f) {
        gridStep = 128.0f;
    } else if (edCam.camera.zoom < 0.75f) {
        gridStep = 64.0f;
    }

    int startX = cast(int)(topLeft.x / gridStep) - 1;
    int endX   = cast(int)(bottomRight.x / gridStep) + 1;
    int startY = cast(int)(topLeft.y / gridStep) - 1;
    int endY   = cast(int)(bottomRight.y / gridStep) + 1;

    Color gridColor = Color(48, 52, 62, 255);

    for (int gx = startX; gx <= endX; gx++) {
        float x = gx * gridStep;
        DrawLineV(Vector2(x, topLeft.y), Vector2(x, bottomRight.y), gridColor);
    }

    for (int gy = startY; gy <= endY; gy++) {
        float y = gy * gridStep;
        DrawLineV(Vector2(topLeft.x, y), Vector2(bottomRight.x, y), gridColor);
    }

    // Ejes principales en el origen (0, 0)
    DrawLineV(Vector2(topLeft.x, 0.0f), Vector2(bottomRight.x, 0.0f), Color(200, 70, 70, 200));
    DrawLineV(Vector2(0.0f, topLeft.y), Vector2(0.0f, bottomRight.y), Color(70, 190, 90, 200));
    DrawCircle(0, 0, 4.0f / edCam.camera.zoom, Colors.YELLOW);
    DrawText("(0, 0)", 6, -16, 12, Color(210, 215, 225, 220));
}

private:

enum float MIN_ZOOM = 0.15f;
enum float MAX_ZOOM = 8.0f;
