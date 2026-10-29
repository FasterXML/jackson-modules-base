package com.fasterxml.jackson.module.blackbird.deser.otherpackage;

import com.fasterxml.jackson.annotation.JsonProperty;

// A subclass in another package can call the protected setter, but only through a
// lookup in that subclass, which Blackbird doesn't always have
public class ProtectedSetterBeanBase
{
    protected String str;

    @JsonProperty("str")
    protected void setStr(String v) {
        str = v;
    }

    public String str() { return str; }
}
