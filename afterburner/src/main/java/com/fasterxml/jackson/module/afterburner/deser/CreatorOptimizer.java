package com.fasterxml.jackson.module.afterburner.deser;

import com.fasterxml.jackson.databind.deser.ValueInstantiator;
import com.fasterxml.jackson.databind.deser.std.StdValueInstantiator;
import com.fasterxml.jackson.databind.introspect.AnnotatedWithParams;
import com.fasterxml.jackson.module.afterburner.util.ClassName;
import com.fasterxml.jackson.module.afterburner.util.DynamicPropertyAccessorBase;
import com.fasterxml.jackson.module.afterburner.util.MyClassLoader;
import com.fasterxml.jackson.module.afterburner.util.bytebuddy.ConstructorCallStackManipulation;
import com.fasterxml.jackson.module.afterburner.util.bytebuddy.SimpleExceptionHandler;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.description.modifier.TypeManifestation;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.scaffold.TypeValidation;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.bytecode.ByteCodeAppender;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.implementation.bytecode.member.MethodReturn;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static net.bytebuddy.description.method.MethodDescription.ForLoadedMethod;
import static net.bytebuddy.description.method.MethodDescription.InDefinedShape;
import static net.bytebuddy.description.type.TypeDescription.ForLoadedType;
import static net.bytebuddy.implementation.bytecode.member.MethodInvocation.invoke;
import static net.bytebuddy.implementation.bytecode.member.MethodVariableAccess.REFERENCE;
import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Helper class that tries to generate {@link ValueInstantiator} class
 * that calls constructors and/or factory methods directly, instead
 * of using Reflection.
 */
public class CreatorOptimizer
    extends DynamicPropertyAccessorBase
{
    protected final Class<?> _valueClass;
    
    protected final MyClassLoader _classLoader;
    
    protected final StdValueInstantiator _originalInstantiator;

    public CreatorOptimizer(Class<?> valueClass, MyClassLoader classLoader,
            StdValueInstantiator orig)
    {
        _valueClass = valueClass;
        _classLoader = classLoader;
        _originalInstantiator = orig;
    }

    public ValueInstantiator createOptimized()
    {
        /* [Issue#11]: Need to avoid optimizing if we use delegate- or
         *  property-based creators.
         */
        if (_originalInstantiator.canCreateFromObjectWith()
                || _originalInstantiator.canCreateUsingDelegate()) {
            return null;
        }
        
        // for now, only consider need to handle default creator
        AnnotatedWithParams defaultCreator = _originalInstantiator.getDefaultCreator();
        if (defaultCreator != null) {
            AnnotatedElement elem = defaultCreator.getAnnotated();
            if (elem instanceof Constructor<?>) {
                // First things first: as per [Issue#34], can NOT access private ctors or methods
                Constructor<?> ctor = (Constructor<?>) elem;
                if (!Modifier.isPrivate(ctor.getModifiers())) {
                    return createSubclass(ctor, null).with(_originalInstantiator);
                }
            } else if (elem instanceof Method) {
                Method m = (Method) elem;
                int mods = m.getModifiers();
                // and as above, can't access private ones
                if (Modifier.isStatic(mods) && !Modifier.isPrivate(mods)) {
                    return createSubclass(null, m).with(_originalInstantiator);
                }
            }
        }
        return null;
    }

    protected OptimizedValueInstantiator createSubclass(Constructor<?> ctor, Method factory)
    {
        MyClassLoader loader = (_classLoader == null) ?
            new MyClassLoader(_valueClass.getClassLoader(), true) : _classLoader;
        final ClassName baseName = ClassName.constructFor(_valueClass, "$Creator4JacksonDeserializer");

        // We need to know checksum even for lookups, so generate it first
        final byte[] bytecode = generateOptimized(baseName, ctor, factory);
        baseName.assignChecksum(bytecode);

        Class<?> impl = null;
        try {
            impl = loader.loadClass(baseName.getDottedName());
        } catch (ClassNotFoundException e) { }
        if (impl == null) {
            impl = loader.loadAndResolve(baseName, bytecode);
        }
        try {
            return (OptimizedValueInstantiator) impl.newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate accessor class '"+baseName+"': "+e.getMessage(), e);
        }
    }

    protected byte[] generateOptimized(ClassName baseName, Constructor<?> ctor, Method factory) {
        final String tmpClassName = baseName.getSlashedTemplate();
        final DynamicType.Builder<?> builder =
                new ByteBuddy(ClassFileVersion.JAVA_V5)
                        .with(TypeValidation.DISABLED)
                        .subclass(OptimizedValueInstantiator.class) //default strategy ensures that all constructors are created
                        .name(tmpClassName)
                        .modifiers(Visibility.PUBLIC, TypeManifestation.FINAL)
                        .method(named("with"))
                        .intercept(
                                //call the constructor of this method that takes a single StdValueInstantiator arg
                                //the required arg is in the position 1 of the method's local variables
                                new Implementation.Simple(
                                        new ByteCodeAppender.Simple(
                                                new ConstructorCallStackManipulation.OfInstrumentedType.OneArg(
                                                        REFERENCE.loadFrom(1)
                                                ),
                                                MethodReturn.REFERENCE
                                        )
                                )

                        )
                        .method(named("createUsingDefault"))
                        .intercept(
                                new SimpleExceptionHandler(
                                        creatorInvokerStackManipulation(ctor, factory),
                                        creatorExceptionHandlerStackManipulation(),
                                        Exception.class,
                                        1 //we added a new local variable in the catch block
                                )
                        );


        return builder.make().getBytes();
    }

    private StackManipulation creatorInvokerStackManipulation(Constructor<?> ctor, Method factory) {
        final StackManipulation invokeManipulation =
                null == ctor ?
                    invoke(new ForLoadedMethod(factory)) :
                    new ConstructorCallStackManipulation.KnownConstructorOfExistingType(ctor);
        return new StackManipulation.Compound(
                invokeManipulation,
                MethodReturn.REFERENCE
        );
    }

    private StackManipulation creatorExceptionHandlerStackManipulation() {
        final TypeDescription typeDescription = new ForLoadedType(OptimizedValueInstantiator.class);
        final InDefinedShape methodDescription =
                typeDescription.getDeclaredMethods().filter(named("_handleInstantiationProblem")).getOnly();

        return new StackManipulation.Compound(
                REFERENCE.storeAt(2), //push exception to new local
                REFERENCE.loadFrom(0), //'this'
                REFERENCE.loadFrom(1), //Arg #1 ("ctxt")
                REFERENCE.loadFrom(2), //exception
                invoke(methodDescription),
                MethodReturn.REFERENCE
        );
    }
}
