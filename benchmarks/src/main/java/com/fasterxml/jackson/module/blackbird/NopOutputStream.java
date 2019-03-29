package com.fasterxml.jackson.module.blackbird;

import java.io.IOException;
import java.io.OutputStream;

import org.openjdk.jmh.infra.Blackhole;

class NopOutputStream extends OutputStream
{
    private Blackhole bh;

    public NopOutputStream(Blackhole bh) {
        this.bh = bh;
    }

    @Override
    public void write(int b) throws IOException {
        bh.consume(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        bh.consume(b);
    }

    @Override
    public void write(byte[] b, int offset, int len) throws IOException {
        bh.consume(b);
    }
}