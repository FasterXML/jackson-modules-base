package com.fasterxml.jackson.module.mrbean;

import java.lang.reflect.Modifier;
import java.util.*;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.Versioned;
import com.fasterxml.jackson.databind.AbstractTypeResolver;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.AnnotatedClassResolver;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.TypeManifestation;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.scaffold.TypeValidation;

import static com.fasterxml.jackson.module.mrbean.TypeDefinitionUtil.createTypeDefinitionFromJavaType;

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
         *<p>
         * NOTE: defaults to `true` since 3.0 (earlier defaulted to `false`)
         */
        FAIL_ON_UNMATERIALIZED_METHOD(true),
        
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
    /**********************************************************
    /* Construction, configuration
    /**********************************************************
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
    public AbstractTypeMaterializer enable(Feature f) {
        _featureFlags |= f.getMask();
        return this;
    }

    /**
     * Method for disabling specified feature.
     */
    public AbstractTypeMaterializer disable(Feature f) {
        _featureFlags &= ~f.getMask();
        return this;
    }

    /**
     * Method for enabling or disabling specified feature.
     */
    public AbstractTypeMaterializer configure(Feature f, boolean state)
    {
        if (state) {
            enable(f);
        } else {
            disable(f);
        }
        return this;
    }

    /**
     * Method for specifying package to use for generated classes.
     */
    public AbstractTypeMaterializer setDefaultPackage(String defPkg)
    {
        if (!defPkg.endsWith(".")) {
            defPkg = defPkg + ".";
        }
        _defaultPackage = defPkg;
        return this;
    }

    /*
    /**********************************************************
    /* Public API
    /**********************************************************
     */

    /**
     * Entry-point for {@link AbstractTypeResolver} that Jackson calls to materialize
     * an abstract type.
     */
    @Override
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

    public Class<?> materializeGenericType(MapperConfig<?> config, JavaType type)
    {
        Class<?> cls = type.getRawClass();
        // Two-phase processing here; first construct concrete intermediate type:
        String abstractName = _defaultPackage+"abstract." +cls.getName()+"_TYPE_RESOLVE";
        byte[] code = buildAbstractBase(type, abstractName);
        Class<?> raw = _classLoader.loadAndResolve(abstractName, code, cls);
        // and only with that intermediate non-generic type, do actual materialization
        AnnotatedClass ac = AnnotatedClassResolver.resolve(config,
                config.getTypeFactory().constructType(raw), config);
        return materializeRawType(config, ac);
    }

    private byte[] buildAbstractBase(JavaType javaType, String className) {
        DynamicType.Builder<?> builder =
                new ByteBuddy()
                //needed because className will contain the 'abstract' Java keyword
                .with(TypeValidation.DISABLED)
                .subclass(createTypeDefinitionFromJavaType(javaType))
                .name(className)
                .modifiers(Visibility.PUBLIC, TypeManifestation.ABSTRACT);
        if (javaType.isInterface()) {
            builder = ByteBuddyBuilderUtil.createEqualsAndHashCode(builder);
        }
        return builder.make().getBytes();
    }

    /**
     * NOTE: should not be called for generic types.
     */
    public Class<?> materializeRawType(MapperConfig<?> config, AnnotatedClass typeDef)
    {
        final JavaType type = typeDef.getType();

        Class<?> rawType = type.getRawClass();
        String newName = _defaultPackage+rawType.getName();
        BeanBuilder builder = BeanBuilder.construct(config, type, typeDef);
        byte[] bytecode = builder.implement(isEnabled(Feature.FAIL_ON_UNMATERIALIZED_METHOD)).build(newName);
        return _classLoader.loadAndResolve(newName, bytecode, rawType);
    }

    private boolean _suitableType(JavaType type)
    {
        // Future plans may include calling of this method for all kinds of abstract types.
        // So as simple precaution, let's limit kinds of types we will try materialize
        // implementations for.
        if (type.isContainerType() || type.isReferenceType()
                || type.isEnumType() || type.isPrimitive()) {
            return false;
        }
        Class<?> cls = type.getRawClass();
        if ((cls == Number.class)
                // 22-Jun-2016, tatu: As per [#12], avoid these too
                || (cls == Date.class) || (cls == Calendar.class)
                || (cls == CharSequence.class) || (cls == Iterable.class) || (cls == Iterator.class)
                ) {
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
    /**********************************************************
    /* Helper classes
    /**********************************************************
     */

    /**
     * To support actual dynamic loading of bytecode we need a simple
     * custom classloader.
     */
    private static class MyClassLoader extends ClassLoader
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
