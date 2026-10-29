package com.fasterxml.jackson.module.blackbird.deser.shadowed.owner;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OwnerBase
{
    public static int setterCalls;

    protected int value;

    @JsonProperty("value")
    protected Object withValue(int v) {
        setterCalls++;
        value = v;
        return this;
    }
}
