package com.fasterxml.jackson.module.noctordeser.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import com.fasterxml.jackson.databind.util.LRUMap;

import sun.reflect.ReflectionFactory;

public class ReflectionUtil
{
    // Limit max number of generated Constructors cached
    private final LRUMap<Class<?>, Constructor<?>> constructorCache = new LRUMap<>(20, 100);

    public Object newConstructorAndCreateInstance(Class<?> classToInstantiate) {
        if (classToInstantiate.isInterface() || Modifier.isAbstract(classToInstantiate.getModifiers())) {
            return null;
        }
        Constructor<?> constructor = constructorCache.get(classToInstantiate);
        try {
            if (constructor == null) {
                constructor = ReflectionFactory.getReflectionFactory()
                        .newConstructorForSerialization(classToInstantiate, Object.class.getDeclaredConstructor());
                constructor.setAccessible(true);
                constructorCache.put(classToInstantiate, constructor);
            }
            return constructor.newInstance();
        } catch (Exception e) {
            return null;
        }
    }
}