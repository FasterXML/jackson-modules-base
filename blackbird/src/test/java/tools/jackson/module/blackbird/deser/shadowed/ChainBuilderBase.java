package tools.jackson.module.blackbird.deser.shadowed;

public class ChainBuilderBase implements Chain
{
    public static int setterCalls;

    protected String level;

    public Chain withLevel(String l) {
        setterCalls++;
        level = l;
        return this;
    }
}
