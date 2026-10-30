package com.fasterxml.jackson.module.blackbird.deser.shadowed;

// declares its own setter, unlike `LevelBean`
public class DirectLevelBean
{
    protected Level level;

    public void setLevel(Level l) {
        level = l;
    }

    public Level level() { return level; }
}
