package com.fasterxml.jackson.module.subtype;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.Module;

/**
 * Subtype module for registering subtypes without annotating the parent class.
 * See <a href="https://github.com/FasterXML/jackson-databind/issues/2104">this issues</a> in jackson-databind.
 */
public class SubtypeModule extends Module {

    protected SubtypeAnnotationIntrospector _introspector;

    public SubtypeModule() {
        this(new SubtypeAnnotationIntrospector());
    }

    public SubtypeModule(SubtypeAnnotationIntrospector introspector) {
        this._introspector = introspector;
    }

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
        context.insertAnnotationIntrospector(_introspector);
    }
}
