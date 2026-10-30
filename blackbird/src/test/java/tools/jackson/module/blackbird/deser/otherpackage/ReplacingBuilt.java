package tools.jackson.module.blackbird.deser.otherpackage;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.module.blackbird.deser.TestBuilders;

@JsonDeserialize(builder=TestBuilders.ReplacingBuilder.class)
public class ReplacingBuilt
{
    public final int first;
    public final int x;

    ReplacingBuilt(int first, int x) {
        this.first = first;
        this.x = x;
    }
}
