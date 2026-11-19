module engine.scene;

nothrow:
@nogc:

import raylib;
import std.stdio;
import core.stdc.stdlib : malloc, free;

struct Scene {
    nothrow void delegate() @nogc setup;
    nothrow void delegate(float) @nogc update;
    nothrow void delegate() @nogc draw;
    nothrow void delegate() @nogc close;
}
