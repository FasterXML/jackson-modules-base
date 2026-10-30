package tools.jackson.module.blackbird.deser.otherpackage;

import com.fasterxml.jackson.annotation.JsonProperty;

// The package-private setter is invisible to subclasses in other packages, but
// Jackson finds it through the annotation and calls it through reflection
public class HiddenSetterBuilderBase
{
    protected int y;

    @JsonProperty("y")
    HiddenSetterBuilderBase withY(int v) {
        y = v;
        return this;
    }
}
