package tools.jackson.module.blackbird.deser.shadowed;

import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder=LevelBuilder.class)
public class LevelBuilt
{
    public final String level;

    LevelBuilt(String level) {
        this.level = level;
    }
}
