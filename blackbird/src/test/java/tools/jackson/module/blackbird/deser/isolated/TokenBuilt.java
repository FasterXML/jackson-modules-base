package tools.jackson.module.blackbird.deser.isolated;

import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder=TokenBuilder.class)
public class TokenBuilt
{
    public final int calls;

    TokenBuilt(int calls) {
        this.calls = calls;
    }
}
