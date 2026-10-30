package com.fasterxml.jackson.module.blackbird.deser.shadowed;

public class LevelBuilderBase<T extends LevelBuilderBase<T>>
{
    public static int setterCalls;

    protected String level;

    @SuppressWarnings("unchecked")
    public T withLevel(Level l) {
        setterCalls++;
        level = l.name();
        return (T) this;
    }
}
