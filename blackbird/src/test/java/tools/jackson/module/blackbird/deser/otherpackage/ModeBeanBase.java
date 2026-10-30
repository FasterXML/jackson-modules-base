package tools.jackson.module.blackbird.deser.otherpackage;

public class ModeBeanBase
{
    public static int setterCalls;

    protected String mode;
    protected String modes;
    protected String name;

    public void setMode(Mode m) {
        setterCalls++;
        mode = m.name();
    }

    public void setModes(Mode[] m) {
        setterCalls++;
        modes = java.util.Arrays.toString(m);
    }

    public void setName(String n) {
        name = n;
    }

    public String mode() { return mode; }
    public String modes() { return modes; }
    public String name() { return name; }
}
