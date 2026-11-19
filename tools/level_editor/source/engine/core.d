module engine.core;

nothrow:
@nogc:

import raylib;
import std.stdio;
import core.stdc.stdlib : malloc, free;
import engine.scene;

private Scene* current_scene;

void engine_init() {
    InitWindow(1920, 1080, "a");
    SetExitKey(KeyboardKey.KEY_NULL);
    SetTargetFPS(75);
}

void engine_close() {
    CloseWindow();
}

void change_scene(Scene* scene) {
    if (current_scene != null) {
        current_scene.close();
    }

    current_scene = scene;

    if (current_scene != null) {
        current_scene.setup();
    }
}

void engine_update() {
    float dt = GetFrameTime();

    if (current_scene != null) {
        current_scene.update(dt);
    }
}

void engine_draw() {
    BeginDrawing();

    if (current_scene != null) {
        current_scene.draw();
    }

    EndDrawing();
}
