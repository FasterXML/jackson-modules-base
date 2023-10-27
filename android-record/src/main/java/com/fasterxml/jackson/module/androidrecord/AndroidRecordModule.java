package com.fasterxml.jackson.module.androidrecord;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.deser.CreatorProperty;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.deser.ValueInstantiator;
import com.fasterxml.jackson.databind.deser.std.StdValueInstantiator;
import com.fasterxml.jackson.databind.introspect.AnnotatedConstructor;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;


/**
 * Module that allows deserialization into records
 * using the canonical constructor on Android,
 * where java records are supported through desugaring,
 * and Jackson's built-in support for records doesn't work,
 * since the desugared classes have a non-standard super class,
 * and record component-related reflection methods are missing.
 *
 * <p>
 * See <a href="https://android-developers.googleblog.com/2023/06/records-in-android-studio-flamingo.html">
 *   Android Developers Blog article</a>
 *
 * <p>
 * Note: this module is a no-op when no Android-desugared records are being deserialized,
 * so it is safe to use in code shared between Android and non-Android platforms.
 *
 * <p>
 * Note: The canonical record constructor is found
 * through matching of parameter types with field types.
 * Therefore, this module doesn't allow a deserialized desugared record class to have a custom
 * constructor with a signature that's any permutation of the canonical one's.
 *
 * @author Eran Leshem
 **/
public class AndroidRecordModule extends SimpleModule {
  @Override
  public void setupModule(SetupContext context) {
    super.setupModule(context);
    context.addValueInstantiators(AndroidRecordModule::findValueInstantiator);
  }

  private static ValueInstantiator findValueInstantiator(DeserializationConfig config, BeanDescription beanDesc,
                                                         ValueInstantiator defaultInstantiator) {
    Class<?> raw = beanDesc.getType().getRawClass();
    if (defaultInstantiator instanceof StdValueInstantiator && raw.getSuperclass() != null
            && raw.getSuperclass().getName().equals("com.android.tools.r8.RecordTag")) {
      Map<Type, Integer> componentTypes = typeMap(Arrays.stream(raw.getDeclaredFields())
              .filter(field -> !Modifier.isStatic(field.getModifiers())).map(Field::getGenericType));
      boolean found = false;
      for (Constructor<?> constructor: raw.getDeclaredConstructors()) {
        Parameter[] parameters = constructor.getParameters();
        Map<Type, Integer> parameterTypes = typeMap(Arrays.stream(parameters).map(Parameter::getParameterizedType));
        if (! parameterTypes.equals(componentTypes)) {
          continue;
        }

        if (found) {
          throw new IllegalArgumentException(String.format(
                          "Multiple constructors match set of components for record %s", raw.getName()));
        }

        SettableBeanProperty[] properties = new SettableBeanProperty[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
          Parameter parameter = parameters[i];
          properties[i] = CreatorProperty.construct(PropertyName.construct(parameter.getName()), config.getTypeFactory()
                          .constructType(parameter.getParameterizedType()), null, null, null, null, i, null, null);
        }

        ((StdValueInstantiator) defaultInstantiator).configureFromObjectSettings(null, null, null, null,
                           new AnnotatedConstructor(null, constructor, null, null), properties);
        constructor.setAccessible(true);
        found = true;
      }
    }

    return defaultInstantiator;
  }

  private static Map<Type, Integer> typeMap(Stream<? extends Type> typeStream) {
    return typeStream.collect(HashMap::new, (map, type) -> map.merge(type, 1, Integer::sum), Map::putAll);
  }
}
