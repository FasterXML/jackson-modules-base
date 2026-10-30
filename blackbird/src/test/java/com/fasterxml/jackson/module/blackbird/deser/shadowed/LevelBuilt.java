package com.fasterxml.jackson.module.blackbird.deser.shadowed;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder=LevelBuilder.class)
public class LevelBuilt
{
    public final String level;

    LevelBuilt(String level) {
        this.level = level;
    }
}
