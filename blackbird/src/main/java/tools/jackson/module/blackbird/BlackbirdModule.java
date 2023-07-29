package tools.jackson.module.blackbird;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.util.function.Function;
import java.util.function.Supplier;

import tools.jackson.core.Version;
import tools.jackson.databind.JacksonModule;
import tools.jackson.module.blackbird.deser.BBDeserializerModifier;
import tools.jackson.module.blackbird.ser.BBSerializerModifier;

public class BlackbirdModule extends JacksonModule
{
    // TODO: replace with jackson-databind/NativeImageUtil.RUNNING_IN_SVM
    private static final boolean RUNNING_IN_SVM = System.getProperty("org.graalvm.nativeimage.imagecode") != null;
    private Function<Class<?>, Lookup> _lookups;

    public BlackbirdModule() {
        this(MethodHandles::lookup);
    }

    public BlackbirdModule(Function<Class<?>, MethodHandles.Lookup> lookups) {
        _lookups = lookups;
    }

    public BlackbirdModule(Supplier<MethodHandles.Lookup> lookup) {
        this(c -> {
            final String className = c.getName();
            return (className.startsWith("java.")
                    // 23-Apr-2021, tatu: [modules-base#131] "sun.misc" problematic too
                    || className.startsWith("sun.misc."))
                ? null : lookup.get();
        });
    }

    @Override
    public void setupModule(SetupContext context)
    {
        if (RUNNING_IN_SVM)
        {
            return;
        }
        CrossLoaderAccess openSesame = new CrossLoaderAccess();
        context.addDeserializerModifier(new BBDeserializerModifier(_lookups, openSesame));
        context.addSerializerModifier(new BBSerializerModifier(_lookups, openSesame));
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

