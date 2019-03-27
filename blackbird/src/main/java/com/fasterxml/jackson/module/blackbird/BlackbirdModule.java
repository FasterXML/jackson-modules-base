package com.fasterxml.jackson.module.blackbird;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.util.function.Function;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.module.blackbird.deser.BBDeserializerModifier;
import com.fasterxml.jackson.module.blackbird.ser.BBSerializerModifier;

public class BlackbirdModule extends Module
{
    private Function<Class<?>, Lookup> _lookups;

    public BlackbirdModule() {
        this(MethodHandles::lookup);
    }

    public BlackbirdModule(Function<Class<?>, MethodHandles.Lookup> lookups) {
        _lookups = lookups;
    }

    public BlackbirdModule(Supplier<MethodHandles.Lookup> lookup) {
        this(c -> c.getPackageName().startsWith("java") ? null : lookup.get());
    }

    @Override
    public void setupModule(SetupContext context)
    {
        context.addBeanDeserializerModifier(new BBDeserializerModifier(_lookups));
        context.addBeanSerializerModifier(new BBSerializerModifier(_lookups));
    }

    @Override
    public String getModuleName() {
        return getClass().getSimpleName();
    }

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }
}

