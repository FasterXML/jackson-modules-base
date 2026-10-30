package tools.jackson.module.blackbird.deser.otherpackage;

import com.fasterxml.jackson.annotation.JsonProperty;

// `withFirst` returns a new instance of this class, not of the subclass the builder
// started as, and reflection can still call the protected `withX` on it
public class ReplacingBuilderBase
{
    protected int first;
    protected int x;

    public ReplacingBuilderBase withFirst(int v) {
        ReplacingBuilderBase b = new ReplacingBuilderBase();
        b.first = v;
        return b;
    }

    @JsonProperty("x")
    protected ReplacingBuilderBase withX(int v) {
        x = v;
        return this;
    }

    public ReplacingBuilt build() {
        return new ReplacingBuilt(first, x);
    }
}
