package com.fasterxml.jackson.module.blackbird;

public final class ClassicBean
{
    public int a, b, c123, d;
    public int e, foobar, g, habitus;

    public ClassicBean setUp() {
        a = 1;
        b = 999;
        c123 = -1000;
        d = 13;
        e = 6;
        foobar = -33;
        g = 0;
        habitus = 123456789;
        return this;
    }

    public void setA(int v) { a = v; }
    public void setB(int v) { b = v; }
    public void setC(int v) { c123 = v; }
    public void setD(int v) { d = v; }

    public void setE(int v) { e = v; }
    public void setF(int v) { foobar = v; }
    public void setG(int v) { g = v; }
    public void setH(int v) { habitus = v; }

    public int getA() { return a; }
    public int getB() { return b; }
    public int getC() { return c123; }
    public int getD() { return d; }
    public int getE() { return e; }
    public int getF() { return foobar; }
    public int getG() { return g; }
    public int getH() { return habitus; }
}