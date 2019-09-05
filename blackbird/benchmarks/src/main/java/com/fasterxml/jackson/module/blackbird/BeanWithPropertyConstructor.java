package com.fasterxml.jackson.module.blackbird;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class BeanWithPropertyConstructor extends SomeBean {
    @SuppressWarnings("unused")
    private BeanWithPropertyConstructor() {
        throw new UnsupportedOperationException();
    }
    @JsonCreator
    public BeanWithPropertyConstructor(
            @JsonProperty("propA") int propA,
            @JsonProperty("propB") String propB,
            @JsonProperty("propC") long propC,
            @JsonProperty("propD") SomeBean propD,
            @JsonProperty("propE") SomeEnum propE)
    {
        setPropA(propA);
        setPropB(propB);
        setPropC(propC);
        setPropD(propD);
        setPropE(propE);
    }
}
