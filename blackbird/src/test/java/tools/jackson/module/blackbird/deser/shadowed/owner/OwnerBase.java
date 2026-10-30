package tools.jackson.module.blackbird.deser.shadowed.owner;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OwnerBase
{
    public static int setterCalls;

    protected int value;
    protected double ratio;

    @JsonProperty("value")
    protected Object withValue(int v) {
        setterCalls++;
        value = v;
        return this;
    }

    // public, but of a type Blackbird doesn't generate setters for
    public void setRatio(double r) {
        ratio = r;
    }

    public double ratio() { return ratio; }
}
