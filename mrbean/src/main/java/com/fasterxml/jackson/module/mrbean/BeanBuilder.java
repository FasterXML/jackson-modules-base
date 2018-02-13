package com.fasterxml.jackson.module.mrbean;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.TypeResolutionContext;
import com.fasterxml.jackson.databind.type.TypeFactory;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.lang.reflect.Modifier;

import static org.objectweb.asm.Opcodes.*;

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
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        String internalClass = getInternalClassName(className);
        String implName = getInternalClassName(_type.getRawClass().getName());
        
        // muchos important: level at least 1.5 to get generics!!!
        // Also: abstract class vs interface...
        String superName;
        if (_type.isInterface()) {
            superName = "java/lang/Object";
            cw.visit(V1_5, ACC_PUBLIC + ACC_SUPER, internalClass, null,
                    superName, new String[] { implName });
        } else {
            superName = implName;
            cw.visit(V1_5, ACC_PUBLIC + ACC_SUPER, internalClass, null,
                    implName, null);
        }
        cw.visitSource(className + ".java", null);
        BeanBuilder.generateDefaultConstructor(cw, superName);
        for (POJOProperty prop : _beanProperties.values()) {
            // First: determine type to use; preferably setter (usually more explicit); otherwise getter
            TypeDescription type = prop.selectType();
            createField(cw, prop, type);
            // since some getters and/or setters may be implemented, check:
            if (!prop.hasConcreteGetter()) {
                createGetter(cw, internalClass, prop, type);
            }
            if (!prop.hasConcreteSetter()) {
                createSetter(cw, internalClass, prop, type);
            }
        }
        for (Method m : _unsupportedMethods.values()) {            
            createUnimplementedMethod(cw, internalClass, m);
        }
        cw.visitEnd();
        return cw.toByteArray();
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

    /**
     * NOTE: only static because it is needed from TypeDetector
     */
    protected static void generateDefaultConstructor(ClassWriter cw, String superName)
    {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        // 26-May-2014, tatu: last 'false' since constructor never owned by interface
        mv.visitMethodInsn(INVOKESPECIAL, superName, "<init>", "()V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0); // don't care (real values: 1,1)
        mv.visitEnd();
    }

    protected void createField(ClassWriter cw, POJOProperty prop, TypeDescription type)
    {
        String sig = type.hasGenerics() ? type.genericSignature() : null;
        String desc = type.erasedSignature();
        /* 15-Mar-2015, tatu: Should not be created public as that can cause problems
         *   like [mrbean#20]
         */
        FieldVisitor fv = cw.visitField(ACC_PROTECTED, prop.getFieldName(), desc, sig, null);
        fv.visitEnd();
    }

    protected void createSetter(ClassWriter cw, String internalClassName,
            POJOProperty prop, TypeDescription propertyType)
    {
        String methodName;
        String desc;
        Method setter = prop.getSetter();
        if (setter != null) { // easy, copy as is
            desc = Type.getMethodDescriptor(setter);
            methodName = setter.getName();
        } else { // otherwise need to explicitly construct from property type (close enough)
            desc = "("+ propertyType.erasedSignature() + ")V";
            methodName = buildSetterName(prop.getName());
        }
        String sig = propertyType.hasGenerics() ? ("("+propertyType.genericSignature()+")V") : null;
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, methodName, desc, sig, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0); // this
        mv.visitVarInsn(propertyType.getLoadOpcode(), 1);
        mv.visitFieldInsn(PUTFIELD, internalClassName, prop.getFieldName(), propertyType.erasedSignature());
        
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0); // don't care (real values: 2, 2)
        mv.visitEnd();
    }

    protected void createGetter(ClassWriter cw, String internalClassName,
            POJOProperty prop, TypeDescription propertyType)
    {
        String methodName;
        String desc;
        Method getter = prop.getGetter();
        if (getter != null) { // easy, copy as is
            desc = Type.getMethodDescriptor(getter);
            methodName = getter.getName();
        } else { // otherwise need to explicitly construct from property type (close enough)
            desc = "()"+propertyType.erasedSignature();
            methodName = buildGetterName(prop.getName());
        }
        
        String sig = propertyType.hasGenerics() ? ("()"+propertyType.genericSignature()) : null;
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, methodName, desc, sig, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0); // load 'this'
        mv.visitFieldInsn(GETFIELD, internalClassName, prop.getFieldName(), propertyType.erasedSignature());
        mv.visitInsn(propertyType.getReturnOpcode());
        mv.visitMaxs(0, 0); // don't care (real values: 1,1)
        mv.visitEnd();
    }

    /**
     * Builder for methods that just throw an exception, basically "unsupported
     * operation" implementation.
     */
    protected void createUnimplementedMethod(ClassWriter cw, String internalClassName,
            Method method)
    {
        String exceptionName = getInternalClassName(UnsupportedOperationException.class.getName());        
        String sig = Type.getMethodDescriptor(method);
        String name = method.getName();
        // should we try to pass generic information?
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, name, sig, null, null);
        mv.visitTypeInsn(NEW, exceptionName);
        mv.visitInsn(DUP);
        mv.visitLdcInsn("Unimplemented method '"+name+"' (not a setter/getter, could not materialize)");
        // 26-May-2014, tatu: last 'false' since constructor never owned by interface
        mv.visitMethodInsn(INVOKESPECIAL, exceptionName, "<init>", "(Ljava/lang/String;)V", false);
        mv.visitInsn(ATHROW);
        mv.visitMaxs(0, 0);  // don't care (real values: 3, 1 + method.getParameterTypes().length);
        mv.visitEnd();
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

    /*
    /**********************************************************
    /* Helper classes
    /**********************************************************
     */

    /**
     * Helper bean used to encapsulate most details of type handling
     */
    static class TypeDescription
    {
        private final Type _asmType;
        private JavaType _jacksonType;

        /*
        /**********************************************************
        /* Construction
        /**********************************************************
         */
        
        public TypeDescription(JavaType type)
        {
            _jacksonType = type;
            _asmType = Type.getType(type.getRawClass());
        }

        /*
        /**********************************************************
        /* Accessors
        /**********************************************************
         */

        public Class<?> getRawClass() { return _jacksonType.getRawClass(); }
        
        public String erasedSignature() {
            return _jacksonType.getErasedSignature();
        }

        public String genericSignature() {
            return _jacksonType.getGenericSignature();
        }

        /**
         * @return True if type has direct generic declaration (which may need
         *   to be copied)
         */
        public boolean hasGenerics() {
            return _jacksonType.hasGenericTypes();
        }
        
        /*
        public boolean isPrimitive() {
            return _signature.length() == 1;
        }
        */

        /*
        public int getStoreOpcode() {
            return _signatureType.getOpcode(ISTORE);
        }
        */

        public int getLoadOpcode() {
            return _asmType.getOpcode(ILOAD);
        }

        public int getReturnOpcode() {
            return _asmType.getOpcode(IRETURN);
        }
        
        @Override
        public String toString() {
            return _jacksonType.toString();
        }

        /*
        /**********************************************************
        /* Other methods
        /**********************************************************
         */

        
        public static TypeDescription moreSpecificType(TypeDescription desc1, TypeDescription desc2)
        {
            Class<?> c1 = desc1.getRawClass();
            Class<?> c2 = desc2.getRawClass();

            if (c1.isAssignableFrom(c2)) { // c2 more specific than c1
                return desc2;
            }
            if (c2.isAssignableFrom(c1)) { // c1 more specific than c2
                return desc1;
            }
            // not compatible, so:
            return null;
        }
    }
}
