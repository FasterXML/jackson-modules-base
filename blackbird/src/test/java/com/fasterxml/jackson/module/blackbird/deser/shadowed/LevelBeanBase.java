package com.fasterxml.jackson.module.blackbird.deser.shadowed;

public class LevelBeanBase
{
    public static int setterCalls;

    protected String level;

    public void setLevel(Level l) {
        setterCalls++;
        level = l.name();
    }

    public String level() { return level; }

    public Level getLevel() {
        return (level == null) ? null : Level.valueOf(level);
    }
}
