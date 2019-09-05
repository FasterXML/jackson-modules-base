package com.fasterxml.jackson.module.blackbird;

import java.util.Random;

import org.apache.commons.lang3.RandomStringUtils;

public class SomeBean {
    public int getPropA() {
        return propA;
    }

    public void setPropA(int propA) {
        this.propA = propA;
    }

    public String getPropB() {
        return propB;
    }

    public void setPropB(String propB) {
        this.propB = propB;
    }

    public long getPropC() {
        return propC;
    }

    public void setPropC(long propC) {
        this.propC = propC;
    }

    public SomeBean getPropD() {
        return propD;
    }

    public void setPropD(SomeBean propD) {
        this.propD = propD;
    }

    public SomeEnum getPropE() {
        return propE;
    }

    public void setPropE(SomeEnum propE) {
        this.propE = propE;
    }

    private int propA;
    private String propB;
    private long propC;
    private SomeBean propD;
    private SomeEnum propE;

    static SomeBean random(Random random) {
        final SomeBean result = new SomeBean();
        result.setPropA(random.nextInt());
        result.setPropB(RandomStringUtils.randomAscii(random.nextInt(32)));
        result.setPropC(random.nextLong());
        if (random.nextInt(5) == 0) {
            result.setPropD(random(random));
        }
        result.setPropE(SomeEnum.values()[random.nextInt(SomeEnum.values().length)]);
        return result;
    }
}