package com.fasterxml.jackson.module.blackbird.deser.otherpackage;

import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.OptBoolean;

// As `HiddenSetterBuilderBase`, but databind makes the setter accessible (for
// injection) before Blackbird sees it
public class InjectedSetterBuilderBase
{
    protected String tag;

    @JacksonInject(value="tag", useInput=OptBoolean.TRUE)
    @JsonProperty("tag")
    InjectedSetterBuilderBase withTag(String v) {
        tag = v;
        return this;
    }
}
