package com.fasterxml.jackson.module.blackbird.deser.shadowed;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder=ChainBuilder.class)
public class ChainBuilt
{
    public final String level;

    ChainBuilt(String level) {
        this.level = level;
    }
}
