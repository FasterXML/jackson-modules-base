package tools.jackson.module.blackbird;

/**
 * Properties of the setter beans in {@link ColdTypes}, with the same names
 * and types as the components of the records there.
 */
public abstract class ColdPojo
{
    private String a;
    private int b;
    private long c;
    private boolean d;
    private String e;
    private Integer f;
    private double g;
    private String h;

    public String getA() { return a; }
    public void setA(String v) { a = v; }
    public int getB() { return b; }
    public void setB(int v) { b = v; }
    public long getC() { return c; }
    public void setC(long v) { c = v; }
    public boolean isD() { return d; }
    public void setD(boolean v) { d = v; }
    public String getE() { return e; }
    public void setE(String v) { e = v; }
    public Integer getF() { return f; }
    public void setF(Integer v) { f = v; }
    public double getG() { return g; }
    public void setG(double v) { g = v; }
    public String getH() { return h; }
    public void setH(String v) { h = v; }
}
