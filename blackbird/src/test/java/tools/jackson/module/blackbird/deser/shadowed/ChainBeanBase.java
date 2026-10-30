package tools.jackson.module.blackbird.deser.shadowed;

public class ChainBeanBase implements Chain
{
    public static int setterCalls;

    protected String level;

    public Chain setLevel(String l) {
        setterCalls++;
        level = l;
        return this;
    }

    public String level() { return level; }
}
