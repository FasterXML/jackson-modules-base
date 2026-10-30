package com.fasterxml.jackson.module.blackbird.deser.otherpackage;

public class ModeBuilderBase<T extends ModeBuilderBase<T>>
{
    public static int setterCalls;

    protected String mode;
    protected String modes;
    protected String name;

    @SuppressWarnings("unchecked")
    public T withMode(Mode m) {
        setterCalls++;
        mode = m.name();
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T withModes(Mode[] m) {
        setterCalls++;
        modes = java.util.Arrays.toString(m);
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T withName(String n) {
        name = n;
        return (T) this;
    }
}
