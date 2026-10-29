package com.fasterxml.jackson.module.blackbird.deser.otherpackage;

import com.fasterxml.jackson.annotation.JsonProperty;

// The package-private setter is invisible to subclasses in other packages, but
// Jackson finds it through the annotation and calls it through reflection
public class HiddenSetterBeanBase
{
    protected int y;

    @JsonProperty("y")
    void setY(int v) {
        y = v;
    }

    public int y() { return y; }
}
