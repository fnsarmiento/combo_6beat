import std.stdio;
import core.stdc.stdlib : malloc, free;
import raylib : WindowShouldClose;

import engine.core;
import engine.scene;

import scenes.map_editor.map_editor;

nothrow:
@nogc:

void main() {
    engine_init();
    change_scene(&map_editor);

    while (!WindowShouldClose()) {
        engine_update();
        engine_draw();
    }

    engine_close();
}

private:
