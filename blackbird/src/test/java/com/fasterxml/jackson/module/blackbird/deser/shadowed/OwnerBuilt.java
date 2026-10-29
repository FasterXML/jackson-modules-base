package com.fasterxml.jackson.module.blackbird.deser.shadowed;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder=OwnerBuilder.class)
public class OwnerBuilt
{
    public final int value;

    OwnerBuilt(int value) {
        this.value = value;
    }
}
