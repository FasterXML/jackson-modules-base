package tools.jackson.module.blackbird.internal;

import java.lang.constant.ConstantDescs;
import java.lang.invoke.MethodHandles;
import java.util.Map;

/**
 * Constant bootstrap for generated code (reader, builder and writer codecs).
 * Not API.
 */
public final class ClassDataBootstrap
{
    private ClassDataBootstrap() {}

    // Resolves one named entry of the hidden class's class data, a Map built
    // at generation time. The JDK's classDataAt bootstrap requires the condy
    // name to be "_", so named entries need this owner. The generated
    // classes are defined in this module (in the codegen package), so they
    // resolve it like any other class of the module. A condy links once and
    // then constant-folds the same as classDataAt; the names exist for dump
    // readability and to remove positional-index bookkeeping from the
    // generators.
    public static Object classDataEntry(MethodHandles.Lookup lookup, String name, Class<?> type)
            throws IllegalAccessException {
        Map<?, ?> data = MethodHandles.classData(lookup, ConstantDescs.DEFAULT_NAME, Map.class);
        Object value = data.get(name);
        if (value == null) {
            throw new IllegalStateException("no class data entry named " + name);
        }
        return type.cast(value);
    }
}
