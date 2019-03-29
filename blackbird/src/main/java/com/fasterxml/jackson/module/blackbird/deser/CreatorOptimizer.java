package com.fasterxml.jackson.module.blackbird.deser;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.deser.ValueInstantiator;
import com.fasterxml.jackson.databind.deser.std.StdValueInstantiator;
import com.fasterxml.jackson.databind.introspect.AnnotatedWithParams;
import com.fasterxml.jackson.module.blackbird.util.Unchecked;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodType.methodType;

/**
 * Helper class that tries to generate {@link ValueInstantiator} class
 * that calls constructors and/or factory methods directly, instead
 * of using Reflection.
 */
public class CreatorOptimizer
{
    protected final Class<?> _valueClass;

    protected final StdValueInstantiator _originalInstantiator;

    private final MethodHandles.Lookup _lookup;

    public CreatorOptimizer(Class<?> valueClass, MethodHandles.Lookup lookup, StdValueInstantiator orig)
    {
        _valueClass = valueClass;
        _originalInstantiator = orig;
        _lookup = lookup;
    }

    public ValueInstantiator createOptimized()
    {
        /* [Issue#11]: Need to avoid optimizing if we use delegate-based creators. */
        if (_originalInstantiator.canCreateUsingDelegate()) {
            return null;
        }

        if (_lookup == null) {
            return null;
        }

        AnnotatedWithParams argsCreator = _originalInstantiator.getWithArgsCreator();
        Function<Object[], Object> optimizedArgsCreator = null;
        if (argsCreator != null) {
            MethodHandle argsCreatorHandle = directHandle(argsCreator.getAnnotated());
            if (argsCreatorHandle != null) {
                    optimizedArgsCreator = Unchecked.supplier(() ->
                        (Function<Object[], Object>) LambdaMetafactory.metafactory(
                            _lookup,
                            "apply",
                            methodType(Function.class, MethodHandle.class),
                            methodType(Object.class, Object.class),
                            lookup().findStatic(
                                CreatorOptimizer.class,
                                "invokeTrampoline",
                                methodType(Object.class, MethodHandle.class, Object[].class)),
                            methodType(Object.class, Object[].class))
                        .getTarget().invokeExact(
                            argsCreatorHandle.asSpreader(
                                    Object[].class,
                                    argsCreatorHandle.type().parameterCount())
                                .asType(methodType(Object.class, Object[].class))))
                        .get();
            }
        }

        AnnotatedWithParams defaultCreator = _originalInstantiator.getDefaultCreator();
        Supplier<?> optimizedFactory = null;
        if (defaultCreator != null) {
            MethodHandle defaultCreatorHandle = directHandle(defaultCreator.getAnnotated());
            if (defaultCreatorHandle != null) {
                optimizedFactory = Unchecked.supplier(() ->
                    (Supplier<?>) LambdaMetafactory.metafactory(
                            _lookup,
                            "get",
                            methodType(Supplier.class),
                            methodType(Object.class),
                            defaultCreatorHandle,
                            defaultCreatorHandle.type())
                        .getTarget().invokeExact())
                    .get();
            }
        }
        if (optimizedArgsCreator != null || optimizedFactory != null) {
            return new OptimizedValueInstantiator(_originalInstantiator, optimizedFactory, optimizedArgsCreator);
        }
        return null;
    }

    private MethodHandle directHandle(AnnotatedElement element) {
        return Stream.concat(
            Stream.of(element)
                .filter(Constructor.class::isInstance)
                .map(Constructor.class::cast)
                .filter(c -> !Modifier.isPrivate(c.getModifiers()))
                .flatMap(t -> {
                    try {
                        return Stream.of(_lookup.unreflectConstructor(t));
                    } catch (IllegalAccessException e) {
                        return Stream.empty();
                    }
                }),
            Stream.of(element)
                .filter(Method.class::isInstance)
                .map(Method.class::cast)
                .filter(m -> {
                    int mods = m.getModifiers();
                    return Modifier.isStatic(mods) && !Modifier.isPrivate(mods);
                })
                .flatMap(t -> {
                    try {
                        return Stream.of(_lookup.unreflect(t));
                    } catch (IllegalAccessException e) {
                        return Stream.empty();
                    }
                }))
        .findFirst().orElse(null);
    }

    // The LambdaMetafactory requires a specifying interface, which is not possible to provide
    // for methods with arbitrary parameter lists.  So we have to use a spread invoker instead,
    // which is not a valid target for the metafactory.  Instead, we wrap it in a trampoline to
    // avoid creating megamorphic code paths and hope that code inlining covers up our reflective sins.
    public static Object invokeTrampoline(MethodHandle delegate, Object[] args) throws Throwable {
        return delegate.invokeExact(args);
    }
}
