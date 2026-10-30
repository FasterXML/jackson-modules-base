package tools.jackson.module.blackbird.deser.shadowed;

import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder=ChainBuilder.class)
public class ChainBuilt
{
    public final String level;

    ChainBuilt(String level) {
        this.level = level;
    }
}
