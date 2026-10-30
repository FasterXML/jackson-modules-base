package tools.jackson.module.blackbird.deser.shadowed;

import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder=OwnerBuilder.class)
public class OwnerBuilt
{
    public final int value;

    OwnerBuilt(int value) {
        this.value = value;
    }
}
