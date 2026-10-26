package tools.jackson.module.blackbird.codegen;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicConstantDesc;

import tools.jackson.module.blackbird.internal.ClassDataBootstrap;

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
    // Generated code resolves class-data entries by NAME through the
    // classDataEntry bootstrap of ClassDataBootstrap (the JDK classDataAt
    // bootstrap rejects any condy name but "_"); see ReaderCodegen.dataName.
    private static final DirectMethodHandleDesc BSM_DATA_ENTRY =
            ConstantDescs.ofConstantBootstrap(of(ClassDataBootstrap.class),
                    "classDataEntry", ConstantDescs.CD_Object);

    private Descs() {}

    static ClassDesc of(Class<?> cls) {
        return cls.describeConstable().orElseThrow();
    }

    /** The constant that loads the class-data entry {@code name} as {@code type}. */
    static DynamicConstantDesc<?> classDataEntry(String name, ClassDesc type) {
        return DynamicConstantDesc.ofNamed(BSM_DATA_ENTRY, name, type);
    }
}
