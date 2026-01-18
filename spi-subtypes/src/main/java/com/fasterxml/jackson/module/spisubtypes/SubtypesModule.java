package com.fasterxml.jackson.module.spisubtypes;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.Module;

/**
 * Subtypes module for registering subtypes without annotating the parent class.
 * See <a href="https://github.com/FasterXML/jackson-databind/issues/2104">this issues</a> in jackson-databind.
 *
 * @since 2.21 / 3.1
 */
public class SubtypesModule extends Module {

    protected SubtypesAnnotationIntrospector _introspector;

    public SubtypesModule() {
        this(new SubtypesAnnotationIntrospector());
    }

    public SubtypesModule(SubtypesAnnotationIntrospector introspector) {
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
