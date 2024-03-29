package com.fasterxml.jackson.module.mrbean;

import java.lang.reflect.Modifier;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.Versioned;
import com.fasterxml.jackson.databind.AbstractTypeResolver;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.AnnotatedClassResolver;

/**
 * Nifty class for pulling implementations of classes out of thin air.
 *<p>
 * ... friends call him Mister Bean... :-)
 * 
 * @author tatu
 * @author sunny
 */
public class AbstractTypeMaterializer
    extends AbstractTypeResolver
    implements Versioned
{
    /**
     * Enumeration that defines togglable features that guide
     * the serialization feature.
     */
    public enum Feature {
        /**
         * Feature that determines what happens if an "unrecognized"
         * (non-getter, non-setter) abstract method is encountered: if set to
         * true, will throw an exception during materialization; if false,
         * will materialize method that throws exception only if called.
         */
        FAIL_ON_UNMATERIALIZED_METHOD(false),
        
        /**
         * Feature that determines what happens when attempt is made to
         * generate implementation of non-public class or interface.
         * If true, an exception is thrown; if false, will just quietly
         * ignore attempts.
         */
        FAIL_ON_NON_PUBLIC_TYPES(true)
        ;

        final boolean _defaultState;

        // Method that calculates bit set (flags) of all features that are enabled by default.
        protected static int collectDefaults() {
            int flags = 0;
            for (Feature f : values()) {
                if (f.enabledByDefault()) {
                    flags |= f.getMask();
                }
            }
            return flags;
        }
                
        private Feature(boolean defaultState) { _defaultState = defaultState; }
        public boolean enabledByDefault() { return _defaultState; }
        public int getMask() { return (1 << ordinal()); }
    }

    /**
     * Bitfield (set of flags) of all Features that are enabled
     * by default.
     */
    protected final static int DEFAULT_FEATURE_FLAGS = Feature.collectDefaults();

    /**
     * Default package to use for generated classes.
     */
    public final static String DEFAULT_PACKAGE_FOR_GENERATED = "com.fasterxml.jackson.module.mrbean.generated.";
    
    /**
     * We will use per-materializer class loader for now; would be nice
     * to find a way to reduce number of class loaders (and hence
     * number of generated classes!) constructed...
     */
    protected final MyClassLoader _classLoader;

    /**
     * Bit set that contains all enabled features
     */
    protected int _featureFlags = DEFAULT_FEATURE_FLAGS;

    /**
     * Package name to use as prefix for generated classes.
     */
    protected String _defaultPackage = DEFAULT_PACKAGE_FOR_GENERATED;
    
    /*
    /**********************************************************************
    /* Construction, configuration
    /**********************************************************************
     */
    
    public AbstractTypeMaterializer() {
        this(null);
    }

    /**
     * @param parentClassLoader Class loader to use for generated classes; if
     *   null, will use class loader that loaded materializer itself.
     */
    public AbstractTypeMaterializer(ClassLoader parentClassLoader)
    {
        if (parentClassLoader == null) {
            parentClassLoader = getClass().getClassLoader();
        }
        _classLoader = new MyClassLoader(parentClassLoader);
    }

    /**
     * Method that will return version information stored in and read from jar
     * that contains this class.
     */
    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }
    
    /**
     * Method for checking whether given feature is enabled or not
     */
    public final boolean isEnabled(Feature f) {
        return (_featureFlags & f.getMask()) != 0;
    }

    /**
     * Method for enabling specified  feature.
     */
    public void enable(Feature f) {
        _featureFlags |= f.getMask();
    }

    /**
     * Method for disabling specified feature.
     */
    public void disable(Feature f) {
        _featureFlags &= ~f.getMask();
    }

    /**
     * Method for enabling or disabling specified feature.
     */
    public void set(Feature f, boolean state)
    {
        if (state) {
            enable(f);
        } else {
            disable(f);
        }
    }

    /**
     * Method for specifying package to use for generated classes.
     */
    public void setDefaultPackage(String defPkg)
    {
        if (!defPkg.endsWith(".")) {
            defPkg = defPkg + ".";
        }
        _defaultPackage = defPkg;
    }

    /*
    /**********************************************************************
    /* Public API
    /**********************************************************************
     */

    /**
     * Entry-point for {@link AbstractTypeResolver} that Jackson calls to materialize
     * an abstract type.
     */
    @Override // since 2.7
    public JavaType resolveAbstractType(DeserializationConfig config, BeanDescription beanDesc)
    {
        final JavaType type = beanDesc.getType();
        if (!_suitableType(type)) {
            return null;
        }

        // might want to skip proxies, local types too... but let them be for now:
        //if (intr.findTypeResolver(beanDesc.getClassInfo(), type) == null) {
        Class<?> materializedType;
        
        if (type.hasGenericTypes()) {
            materializedType = materializeGenericType(config, type);
        } else {
            materializedType = materializeRawType(config, beanDesc.getClassInfo());
        }
        return config.constructType(materializedType);
    }

    /**
     * @since 2.4
     */
    public Class<?> materializeGenericType(MapperConfig<?> config, JavaType type)
    {
        Class<?> cls = type.getRawClass();
        // Two-phase processing here; first construct concrete intermediate type:
        String abstractName = _defaultPackage+"abstract." +cls.getName()+"_TYPE_RESOLVE";
        TypeBuilder tb = new TypeBuilder(type);
        byte[] code = tb.buildAbstractBase(abstractName);
        Class<?> raw = _loadAndResolve(abstractName, code, cls);
        // and only with that intermediate non-generic type, do actual materialization
        AnnotatedClass ac = AnnotatedClassResolver.resolve(config,
                config.getTypeFactory().constructType(raw), config);
        final String implClassName = _defaultPackage + cls.getName();
        return _materializeRawType(config, ac, implClassName);
    }

    /**
     * NOTE: should not be called for generic types.
     * 
     * @since 2.4
     */
    public Class<?> materializeRawType(MapperConfig<?> config, AnnotatedClass typeDef) {
        Class<?> rawType = typeDef.getRawType();
        final String implClassName = _defaultPackage + rawType.getName();
        return _materializeRawType(config, typeDef, implClassName);
    }

    // @since 2.12 refactored to allow passing specific name to use; needed to support
    //   both generic and non-generic cases
    protected Class<?> _materializeRawType(MapperConfig<?> config, AnnotatedClass typeDef,
            String nameToUse)
    {
        final JavaType type = typeDef.getType();
        Class<?> rawType = type.getRawClass();
        BeanBuilder builder = BeanBuilder.construct(config, type, typeDef);
        byte[] bytecode = builder.implement(isEnabled(Feature.FAIL_ON_UNMATERIALIZED_METHOD)).build(nameToUse);
        return _loadAndResolve(nameToUse, bytecode, rawType);
    }

    // @since 2.12
    protected Class<?> _loadAndResolve(String className, byte[] bytecode, Class<?> rawType) {
        return _classLoader.loadAndResolve(className, bytecode, rawType);
    }

    /**
     * Overridable helper method called to check if given non-concrete type
     * should be materialized.
     *<p>
     * Default implementation will blocks all
     *<ul>
     * <li>primitive types</li>
     * <li>{@code Enums}</li>
     * <li>Container types (Collections, Maps; as per Jackson "container type")</li>
     * <li>Reference types (Jackson definition</li>
     *</ul>
     *<p>
     * Jackson 2.12 and earlier enumerated a small set of other types under
     * {@link java.lang} and {@link java.util}: 2.13 and later simply block
     * all types in {@code java.*}.
     *
     * @param type Type that we are asked to materialize
     *
     * @return True if materialization should proceed; {@code false} if not.
     */
    protected boolean _suitableType(JavaType type)
    {
        // Future plans may include calling of this method for all kinds of abstract types.
        // So as simple precaution, let's limit kinds of types we will try materialize
        // implementations for.
        if (type.isContainerType() || type.isReferenceType()
                || type.isEnumType() || type.isPrimitive()) {
            return false;
        }
        final Class<?> cls = type.getRawClass();

        // In Jackson 2.12 we had:
        /*
        if ((cls == Number.class)
                // 22-Jun-2016, tatu: As per [#12], avoid these too
                || (cls == Date.class) || (cls == Calendar.class)
                || (cls == CharSequence.class) || (cls == Iterable.class) || (cls == Iterator.class)
                // 06-Feb-2019, tatu: [modules-base#74] and:
                || (cls == java.io.Serializable.class)
                || (cls == java.util.TimeZone.class)) {
                ) {
                */
        // 23-Apr-2021, tatu: Jackson 2.13, as per [modules-base#132], do this:
        if (cls.getName().startsWith("java.")) {
            return false;
        }

        // Fail on non-public classes, since we can't easily force  access to such
        // classes (unless we tried to generate impl classes in same package)
        if (!Modifier.isPublic(cls.getModifiers())) {
            if (isEnabled(Feature.FAIL_ON_NON_PUBLIC_TYPES)) {
                throw new IllegalArgumentException("Can not materialize implementation of "+cls+" since it is not public ");
            }
            return false;
        }
        return true;
    }

    /*
    /**********************************************************************
    /* Helper classes
    /**********************************************************************
     */

    /**
     * To support actual dynamic loading of bytecode we need a simple
     * custom classloader.
     */
    static class MyClassLoader extends ClassLoader
    {
        public MyClassLoader(ClassLoader parent)
        {
            super(parent);
        }

        /**
         * @param targetClass Interface or abstract class that class to load should extend or 
         *   implement
         */
        public Class<?> loadAndResolve(String className, byte[] byteCode, Class<?> targetClass)
            throws IllegalArgumentException
        {
            // First things first: just to be sure; maybe we have already loaded it?
            Class<?> old = findLoadedClass(className);
            if (old != null && targetClass.isAssignableFrom(old)) {
                return old;
            }

            Class<?> impl;
            try {
                impl = defineClass(className, byteCode, 0, byteCode.length);
            } catch (LinkageError e) {
                throw new IllegalArgumentException("Failed to load class '"+className+"': "+e.getMessage() ,e);
            }
            // important: must also resolve the class...
            resolveClass(impl);
            return impl;
        }
    }
}
