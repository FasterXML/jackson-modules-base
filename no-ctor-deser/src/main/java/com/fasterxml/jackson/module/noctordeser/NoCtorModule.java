package com.fasterxml.jackson.module.noctordeser;

import com.fasterxml.jackson.core.Version;

import com.fasterxml.jackson.databind.JacksonModule;

public class NoCtorModule extends JacksonModule
{
    @Override
    public String getModuleName() {
        return getClass().getSimpleName();
    }

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    @Override
    public void setupModule(SetupContext context) {
        context.addHandler(new MissingInstantiatorHandler());
    }
}
