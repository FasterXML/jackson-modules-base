package tools.jackson.module.blackbird.codegen;

import java.lang.constant.ClassDesc;

/**
 * Class descriptors for generated code, derived from class literals so that
 * every referenced type is compile-checked and follows renames. Two build
 * facts rely on the literals as well: javac's {@code --patch-module} test
 * build emits only compile-time-referenced classes into the test module, and
 * a descriptor built from a name would silently point at a class that build
 * left out.
 */
final class Descs
{
    private Descs() {}

    static ClassDesc of(Class<?> cls) {
        return cls.describeConstable().orElseThrow();
    }
}
