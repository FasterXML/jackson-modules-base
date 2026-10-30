package com.fasterxml.jackson.module.blackbird.deser.shadowed;

public final class LevelBuilder extends LevelBuilderBase<LevelBuilder>
{
    public LevelBuilt build() {
        return new LevelBuilt(level);
    }
}
