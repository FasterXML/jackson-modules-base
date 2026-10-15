package tools.jackson.module.blackbird;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

import tools.jackson.core.Version;

import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.util.NativeImageUtil;

import tools.jackson.module.blackbird.codegen.ClassFileEmitters;
import tools.jackson.module.blackbird.deser.BBDeserializerModifier;
import tools.jackson.module.blackbird.internal.DiscoveredBlackbirdModule;
import tools.jackson.module.blackbird.ser.BBSerializerModifier;

/**
 * Registers Blackbird's generated per-bean codecs. No access configuration is
 * needed: generated codecs reach members through handles unreflected after
 * databind's own access checks, so whatever stock databind can read or write
 * accelerates (on the module path, the same {@code opens ... to
 * tools.jackson.databind} stock needs). The lookup-supplying constructors and
 * the {@code findLookup}/{@code findLookupSupplier} overrides remain
 * supported released API, but no acceleration requires a lookup any more.
 *<p>
 * Code generation needs Java 25 or later: the generators use the ClassFile
 * API (final in Java 24) and compile at release 25. On Java 17 to 24, module
 * discovery ({@code MapperBuilder.findAndAddModules()}) skips this module with
 * one logged warning, so that discovery still registers every other module.
 * An explicit registration fails with an {@link IllegalStateException},
 * whether or not discovery runs too.
 */
public class BlackbirdModule extends JacksonModule
    implements java.io.Serializable // @since 3.1
{
    private static final long serialVersionUID = 3L;

    private static final AtomicBoolean WARNED_INACTIVE = new AtomicBoolean();

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
        if (!ClassFileEmitters.available()) {
            // Discovery finds the jar wherever it is on the path, also where
            // nobody asked for Blackbird; an explicit registration did ask.
            if (this instanceof DiscoveredBlackbirdModule) {
                if (WARNED_INACTIVE.compareAndSet(false, true)) {
                    System.getLogger("tools.jackson.module.blackbird").log(
                            System.Logger.Level.WARNING,
                            "jackson-module-blackbird, found through module discovery, is inactive: "
                                    + _unavailableReason() + ". Mappers work without it.");
                }
                return;
            }
            throw new IllegalStateException("Cannot register BlackbirdModule: "
                    + _unavailableReason() + ". Remove the registration on this runtime"
                    + " (jackson-module-afterburner supports Java 17 and later).");
        }
        Function<Class<?>, Lookup> lookup = findLookup();
        context.addDeserializerModifier(new BBDeserializerModifier(lookup));
        context.addSerializerModifier(new BBSerializerModifier(lookup));
    }

    private static String _unavailableReason() {
        Runtime.Version version = Runtime.version();
        if (version.feature() < 25) {
            return "it requires Java 25 or later, and this runtime is Java " + version;
        }
        return "its Java 25 classes (META-INF/versions/25 of the jar) are not visible on Java "
                + version + "; the jar was probably repackaged without multi-release support";
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

