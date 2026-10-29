package com.fasterxml.jackson.module.blackbird.deser.otherpackage;

public class ModeBeanBase
{
    public static int setterCalls;

    protected String mode;
    protected String name;

    public void setMode(Mode m) {
        setterCalls++;
        mode = m.name();
    }

    public void setName(String n) {
        name = n;
    }

    public String mode() { return mode; }
    public String name() { return name; }
}
