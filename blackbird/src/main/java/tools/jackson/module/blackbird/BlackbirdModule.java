package tools.jackson.module.blackbird;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.util.function.Function;
import java.util.function.Supplier;

import tools.jackson.core.Version;

import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.util.NativeImageUtil;

import tools.jackson.module.blackbird.deser.BBDeserializerModifier;
import tools.jackson.module.blackbird.ser.BBSerializerModifier;

public class BlackbirdModule extends JacksonModule
    implements java.io.Serializable // @since 3.1
{
    private static final long serialVersionUID = 3L;

    // 13-Feb-2026, tatu: [blackbird#334] This is a mess due to need for
    //    backwards-compatibility... but has to be

    private final Supplier<MethodHandles.Lookup> _lookupSupplier;

    private final Function<Class<?>, MethodHandles.Lookup> _lookupFunction;

    public BlackbirdModule() {
        _lookupSupplier = null;
        _lookupFunction = null;
    }

    /**
     * @since 3.1 Use default (no-args) constructor and override
     *    {@link #findLookupSupplier()}) instead.
     */
    @Deprecated // @since 3.1
    public BlackbirdModule(Supplier<MethodHandles.Lookup> lookupS) {
        _lookupSupplier = lookupS;
        _lookupFunction = null;
    }

    /**
     * @since 3.1 Use default (no-args) constructor and override
     *  {@link #findLookup}) instead.
     */
    @Deprecated // @since 3.1
    public BlackbirdModule(Function<Class<?>, MethodHandles.Lookup> lookupF) {
        _lookupSupplier = null;
        _lookupFunction = lookupF;
    }

    /**
     * Overridable method module uses to access {@code MethodHandles.Lookup} supplier;
     * needed to keep module itself {@link java.io.Serializable}.
     *
     * @since 3.1
     */
    protected Function<Class<?>, Lookup> findLookup() {
        if (_lookupFunction != null) {
            return _lookupFunction;
        }
        return _wrapWithJdkClassCheck(findLookupSupplier());
    }

    /**
     * Overridable method module uses to access {@code MethodHandles.Lookup} supplier
     * used to create actual lookup {@code Function}
     *
     * @since 3.1
     */
    protected Supplier<MethodHandles.Lookup> findLookupSupplier() {
        return (_lookupSupplier != null)
                ? _lookupSupplier
                : MethodHandles::lookup;
        
    }
    
    protected Function<Class<?>, Lookup> _wrapWithJdkClassCheck(Supplier<MethodHandles.Lookup> lookup)
    {
        return c -> {
            final String className = c.getName();
            return (className.startsWith("java.")
                    // 23-Apr-2021, tatu: [modules-base#131] "sun.misc" problematic too
                    || className.startsWith("sun.misc."))
                ? null : lookup.get();
        };
    }

    @Override
    public void setupModule(SetupContext context)
    {
        // [modules-base#191] Since 2.16, Native image detection 
        if (NativeImageUtil.isInNativeImage())
        {
            return;
        }
        Function<Class<?>, Lookup> lookup = findLookup();
        CrossLoaderAccess openSesame = new CrossLoaderAccess();
        context.addDeserializerModifier(new BBDeserializerModifier(lookup, openSesame));
        context.addSerializerModifier(new BBSerializerModifier(lookup, openSesame));
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

