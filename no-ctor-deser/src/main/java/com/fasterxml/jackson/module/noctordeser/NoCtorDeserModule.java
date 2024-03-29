package com.fasterxml.jackson.module.noctordeser;

import com.fasterxml.jackson.core.Version;

/**
 * @since 2.13.1 (in 2.13.0 had wrong name, {@code NoCtorModule})
 */
public class NoCtorDeserModule extends com.fasterxml.jackson.databind.Module
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
        context.addDeserializationProblemHandler(new MissingInstantiatorHandler());
    }
}
