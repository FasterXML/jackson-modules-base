package com.fasterxml.jackson.module.mrbean;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.TypeResolutionContext;
import com.fasterxml.jackson.databind.type.TypeFactory;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.scaffold.TypeValidation;
import net.bytebuddy.implementation.ExceptionMethod;
import net.bytebuddy.implementation.FieldAccessor;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.fasterxml.jackson.module.mrbean.ByteBuddyBuilderUtil.createEqualsAndHashCode;
import static com.fasterxml.jackson.module.mrbean.TypeDefinitionUtil.createTypeDefinitionFromJavaType;

/**
 * Heavy lifter of mr Bean package: class that keeps track of logical POJO properties,
 * and figures out how to create an implementation class.
 */
public class BeanBuilder
{
    protected Map<String, POJOProperty> _beanProperties = new LinkedHashMap<String,POJOProperty>();
    protected LinkedHashMap<String,Method> _unsupportedMethods = new LinkedHashMap<String,Method>();

    /**
     * Abstract class or interface that the bean is created to extend or implement.
     */
    protected final JavaType _type;

    protected final AnnotatedClass _typeDefinition;

    protected final TypeFactory _typeFactory;

    public BeanBuilder(JavaType type, AnnotatedClass ac, TypeFactory tf)
    {
        _type = type;
        _typeDefinition = ac;
        _typeFactory = tf;
    }

    public static BeanBuilder construct(MapperConfig<?> config, JavaType type, AnnotatedClass ac)
    {
        return new BeanBuilder(type, ac, config.getTypeFactory());
    }

    /*
    /**********************************************************
    /* Core public API
    /**********************************************************
     */

    /**
     * @param failOnUnrecognized If true, and an unrecognized (non-getter, non-setter)
     *   method is encountered, will throw {@link IllegalArgumentException}; if false,
     *   will implement bogus method that will throw {@link UnsupportedOperationException}
     *   if called.
     */
    public BeanBuilder implement(boolean failOnUnrecognized)
    {
        ArrayList<JavaType> implTypes = new ArrayList<JavaType>();
        // First: find all supertypes:
        implTypes.add(_type);
        BeanUtil.findSuperTypes(_type, Object.class, implTypes);
        final boolean hasConcrete = !_type.isInterface();
        
        for (JavaType impl : implTypes) {
            TypeResolutionContext ctxt = buildTypeContext(impl);

            // and then find all getters, setters, and other non-concrete methods therein:
            for (Method m : impl.getRawClass().getDeclaredMethods()) {
                // 15-Sep-2015, tatu: As per [module-mrbean#25], make sure to ignore static
                //    methods.
                if (Modifier.isStatic(m.getModifiers())) {
                    continue;
                }
                String methodName = m.getName();
                int argCount = m.getParameterTypes().length;
                if (argCount == 0) { // getter?
                    if (methodName.startsWith("get")) {
                        if (methodName.length() > 3) { // ignore plain "get()"
                            addGetter(ctxt, m);
                            continue;
                        }
                    } else if (methodName.startsWith("is")) {
                        if (methodName.length() > 2) { // ignore plain "is()"
                            if (returnsBoolean(m)) {
                                addGetter(ctxt, m);
                                continue;
                            }
                        }
                    }
                } else if ((argCount == 1) && methodName.startsWith("set")) { // ignore "set()"
                    if (methodName.length() > 3) {
                        addSetter(ctxt, m);
                        continue;
                    }
                }
                // Otherwise, if concrete, or already handled, skip:
                if (BeanUtil.isConcrete(m) || _unsupportedMethods.containsKey(methodName)) {
                    continue;
                }
                // [module-mrbean#11]: try to support overloaded methods
                if (hasConcrete && hasConcreteOverride(m, _type)) {
                    continue;
                }
                if (failOnUnrecognized) {
                    throw new IllegalArgumentException("Unrecognized abstract method '"+methodName
                            +"' (not a getter or setter) -- to avoid exception, disable AbstractTypeMaterializer.Feature.FAIL_ON_UNMATERIALIZED_METHOD");
                }
                _unsupportedMethods.put(methodName, m);
            }
        }

        return this;
    }

    /**
     * Method that generates byte code for class that implements abstract
     * types requested so far.
     * 
     * @param className Fully-qualified name of the class to generate
     * @return Byte code Class instance built by this builder
     */
    public byte[] build(String className)
    {
        DynamicType.Builder<?> builder = new ByteBuddy()
                                                //needed because className can contain Java keywords
                                                .with(TypeValidation.DISABLED)
                                                .subclass(_type.getRawClass())
                                                .name(className);
        for (POJOProperty prop : _beanProperties.values()) {
            final TypeDefinition typeDefinition = getFieldType(prop);
            builder = createField(builder, prop, typeDefinition);
            if (!prop.hasConcreteGetter()) {
                builder = createGetter(builder, prop, typeDefinition);
            }
            if (!prop.hasConcreteSetter()) {
                builder = createSetter(builder, prop, typeDefinition);
            }
        }
        for (Method m : _unsupportedMethods.values()) {
            builder = builder
                        .defineMethod(m.getName(), m.getReturnType(), Visibility.PUBLIC)
                        .intercept(
                            ExceptionMethod.throwing(
                                UnsupportedOperationException.class,
                                "Unimplemented method '"+m.getName()+"' (not a setter/getter, could not materialize)")
                        );
        }
        if (_type.isInterface()) {
            builder = createEqualsAndHashCode(builder);
        }
        return builder.make().getBytes();
    }

    /*
    /**********************************************************
    /* Internal methods, property discovery
    /**********************************************************
     */

    /**
     * Helper method used to detect if an abstract method found in a base class
     * may actually be implemented in a (more) concrete sub-class.
     * 
     * @since 2.4
     */
    protected boolean hasConcreteOverride(Method m0, JavaType implementedType)
    {
        final String name = m0.getName();
        final Class<?>[] argTypes = m0.getParameterTypes();
        for (JavaType curr = implementedType; (curr != null) && !curr.isJavaLangObject();
                curr = curr.getSuperClass()) {
            // 29-Nov-2015, tatu: Avoiding exceptions would be good, so would linear scan
            //    be better here?
            try {
                Method effectiveMethod = curr.getRawClass().getDeclaredMethod(name, argTypes);
                if (effectiveMethod != null && BeanUtil.isConcrete(effectiveMethod)) {
                    return true;
                }
            } catch (NoSuchMethodException e) { }
        }
        return false;
    }
    
    protected String getPropertyName(String methodName)
    {
        int prefixLen = methodName.startsWith("is") ? 2 : 3;
        return decap(methodName.substring(prefixLen));
    }

    protected String buildGetterName(String fieldName) {
        return cap("get", fieldName);
    }

    protected String buildSetterName(String fieldName) {
        return cap("set", fieldName);
    }

    protected String getInternalClassName(String className) {
        return className.replace(".", "/");
    }

    protected void addGetter(TypeResolutionContext ctxt, Method m)
    {
        POJOProperty prop = findProperty(ctxt, getPropertyName(m.getName()));
        // only set if not yet set; we start with super class:
        if (prop.getGetter() == null) {
            prop.setGetter(m);        
        }
    }

    protected void addSetter(TypeResolutionContext ctxt, Method m)
    {
        POJOProperty prop = findProperty(ctxt, getPropertyName(m.getName()));
        if (prop.getSetter() == null) {
            prop.setSetter(m);
        }
    }

    protected POJOProperty findProperty(TypeResolutionContext ctxt, String propName)
    {
        POJOProperty prop = _beanProperties.get(propName);
        if (prop == null) {
            prop = new POJOProperty(ctxt, propName);
            _beanProperties.put(propName, prop);
        }
        return prop;
    }
    
    protected final static boolean returnsBoolean(Method m)
    {
        Class<?> rt = m.getReturnType();
        return (rt == Boolean.class || rt == Boolean.TYPE);
    }
    
    /*
    /**********************************************************
    /* Internal methods, bytecode generation
    /**********************************************************
     */

    private TypeDefinition getFieldType(POJOProperty property) {
        return createTypeDefinitionFromJavaType(property.selectType());
    }

    private DynamicType.Builder<?> createField(DynamicType.Builder<?> builder,
                                               POJOProperty property,
                                               TypeDefinition typeDefinition) {
        return builder.defineField(
                property.getName(),
                typeDefinition,
                Visibility.PROTECTED
        );
    }

    private DynamicType.Builder<?> createGetter(DynamicType.Builder<?> builder,
                                                POJOProperty property,
                                                TypeDefinition typeDefinition)
    {
        final String methodName = property.getGetter() != null
                ? property.getGetter().getName() //if the getter exists, use it's name because it could be like 'isXXX'
                : buildGetterName(property.getName());
        return builder
                    .defineMethod(methodName, typeDefinition)
                    .intercept(FieldAccessor.ofBeanProperty());
    }

    private DynamicType.Builder<?> createSetter(DynamicType.Builder<?> builder,
                                                POJOProperty property,
                                                TypeDefinition typeDefinition)
    {
        return builder
                .defineMethod(buildSetterName(property.getName()), Void.TYPE, Visibility.PUBLIC)
                .withParameters(typeDefinition)
                .intercept(FieldAccessor.ofBeanProperty());
    }

    /*
    /**********************************************************
    /* Internal methods, other
    /**********************************************************
     */
    
    protected String decap(String name) {
        char c = name.charAt(0);
        if (name.length() > 1
                && Character.isUpperCase(name.charAt(1))
                && Character.isUpperCase(c)){
            return name;
        }
        char chars[] = name.toCharArray();
        chars[0] = Character.toLowerCase(c);
        return new String(chars);
    }

    protected String cap(String prefix, String name)
    {
        final int plen = prefix.length();
        StringBuilder sb = new StringBuilder(plen + name.length());
        sb.append(prefix);
        sb.append(name);
        sb.setCharAt(plen, Character.toUpperCase(name.charAt(0)));
        return sb.toString();
    }

    protected TypeResolutionContext buildTypeContext(JavaType ctxtType)
    {
        return new TypeResolutionContext.Basic(_typeFactory,
                ctxtType.getBindings());
    }
}
