package blackbird.jpms.test.beans;

import java.lang.invoke.MethodHandles;

/** Hands tests this module's lookup and its non-public bean type. */
public final class Beans {
    private Beans() {}

    public static MethodHandles.Lookup lookup() {
        return MethodHandles.lookup();
    }

    public static Class<?> pkgBeanClass() {
        return PkgBean.class;
    }
}
