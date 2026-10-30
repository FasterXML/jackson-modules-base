package tools.jackson.module.blackbird.deser.shadowed;

public final class ChainBuilder extends ChainBuilderBase
{
    public ChainBuilt build() {
        return new ChainBuilt(level);
    }
}
