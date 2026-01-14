package com.fasterxml.jackson.module.subtype;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.jsontype.NamedType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Annotation introspector that handles {@link JacksonSubType} annotation.
 * <p>
 * It caches the subclasses of a parent class, so it's not-real-time.
 * When the parent class not found in cache,
 * it will try to load all found child classes via SPI then cache it.
 * We can remove the parent class in the cache by {@link #unregisterType}.
 * </p>
 */
public class SubtypeAnnotationIntrospector extends AnnotationIntrospector {
    private final ConcurrentHashMap<Class<?>, List<NamedType>> subtypes = new ConcurrentHashMap<>();

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    @Override
    public List<NamedType> findSubtypes(Annotated a) {
        registerTypes(a.getRawType());

        List<NamedType> list1 = _findSubtypes(a.getRawType(), a::getAnnotation);
        List<NamedType> list2 = subtypes.getOrDefault(a.getRawType(), Collections.emptyList());

        if (list1.isEmpty()) return list2;
        if (list2.isEmpty()) return list1;
        List<NamedType> list = new ArrayList<>(list1.size() + list2.size());
        list.addAll(list1);
        list.addAll(list2);
        return list;
    }

    /**
     * load parent's subclass by SPI.
     *
     * @param parent parent class.
     * @param <S>    parent class type.
     */
    @SuppressWarnings("unchecked")
    public <S> void registerTypes(Class<S> parent) {
        // If parent is already registered (either by spi or manually by the user), then skip it
        if (subtypes.containsKey(parent)) {
            return;
        }
        List<Class<S>> subclasses = new ArrayList<>();
        for (S instance : ServiceLoader.load(parent)) {
            subclasses.add((Class<S>) instance.getClass());
        }
        this.registerTypes(parent, subclasses);
    }

    /**
     * register subtypes without SPI.
     * Of course, you need to provide them :)
     *
     * @param parent:     parent class.
     * @param subclasses: children class.
     * @param <S>:        parent class type.
     */
    public <S> void registerTypes(Class<S> parent, Iterable<Class<S>> subclasses) {
        List<NamedType> result = new ArrayList<>();
        for (Class<S> subclass : subclasses) {
            result.addAll(_findSubtypes(subclass, subclass::getAnnotation));
        }
        subtypes.put(parent, result);
    }

    /**
     * remove the parent class in the cache,
     * so that {@link #registerTypes(Class)} can re-look by SPI.
     *
     * @param parent: parent class.
     */
    public void unregisterType(Class<?> parent) {
        subtypes.remove(parent);
    }

    /**
     * find all {@link JacksonSubType} names.
     *
     * @param clazz  class which annotate with {@link JacksonSubType}.
     * @param getter getAnnotation.
     * @param <S>    class type.
     * @return all names.
     */
    private <S> List<NamedType> _findSubtypes(Class<S> clazz, Function<Class<JacksonSubType>, JacksonSubType> getter) {
        if (clazz == null) {
            return Collections.emptyList();
        }
        JacksonSubType subtype = getter.apply(JacksonSubType.class);
        if (subtype == null) {
            return Collections.emptyList();
        }
        List<NamedType> result = new ArrayList<>();
        result.add(new NamedType(clazz, subtype.value()));
        // [databind#2761]: alternative set of names to use
        for (String name : subtype.names()) {
            result.add(new NamedType(clazz, name));
        }
        return result;
    }
}
