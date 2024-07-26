package com.fasterxml.jackson.module.androidrecord;

import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.deser.CreatorProperty;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.deser.ValueInstantiator;
import com.fasterxml.jackson.databind.deser.std.StdValueInstantiator;
import com.fasterxml.jackson.databind.introspect.AccessorNamingStrategy;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.AnnotatedConstructor;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import com.fasterxml.jackson.databind.introspect.AnnotatedParameter;
import com.fasterxml.jackson.databind.introspect.BasicClassIntrospector;
import com.fasterxml.jackson.databind.introspect.DefaultAccessorNamingStrategy;
import com.fasterxml.jackson.databind.introspect.POJOPropertiesCollector;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.util.ClassUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * Module that allows (de)serialization of records using the canonical constructor and accessors on Android,
 * where java records are supported through desugaring, and Jackson's built-in support for records doesn't work,
 * since the desugared classes have a non-standard super class,
 * and record component-related reflection methods are missing.
 *
 * <p>
 * See <a href="https://android-developers.googleblog.com/2023/06/records-in-android-studio-flamingo.html">
 *   Android Developers Blog article</a>
 *
 * <p>
 * Note: this module is a no-op when no Android-desugared records are being (de)serialized,
 * so it is safe to use in code shared between Android and non-Android platforms.
 *
 * <p>
 * Note: the canonical record constructor is identified through matching of parameter names and types with fields.
 * Therefore, this module doesn't allow a deserialized desugared record to have a custom constructor
 * with the same set of parameter names and types as the canonical one.
 * For the same reason, this module requires that desugared canonical record constructor parameter names
 * be stored in class files. Apparently, with Android SDK 34 tooling, that is the case by default.
 * If that ever changes, it may require an explicit setting in build files.
 *
 * @author Eran Leshem
 **/
public class AndroidRecordModule extends SimpleModule {
  private static final long serialVersionUID = 1L;

  private static final class AndroidRecordNaming
      extends DefaultAccessorNamingStrategy
  {
      /**
       * Names of actual Record components from definition; auto-detected.
       */
      private final Set<String> _componentNames;

      AndroidRecordNaming(MapperConfig<?> config, AnnotatedClass forClass) {
          super(config, forClass,
                  // no setters for (immutable) Records:
                  null,
                  "get", "is", null);
          _componentNames = getDesugaredRecordComponents(forClass.getRawType()).map(Field::getName)
                  .collect(Collectors.toSet());
      }

      @Override
      public String findNameForRegularGetter(AnnotatedMethod am, String name)
      {
          // By default, field names are un-prefixed, but verify so that we will not
          // include "toString()" or additional custom methods (unless latter are
          // annotated for inclusion)
          if (_componentNames.contains(name)) {
              return name;
          }
          // but also allow auto-detecting additional getters, if any?
          return super.findNameForRegularGetter(am, name);
      }
  }

  static class AndroidRecordClassIntrospector extends BasicClassIntrospector {
    private static final long serialVersionUID = 1L;

    @Override
    protected POJOPropertiesCollector collectProperties(MapperConfig<?> config, JavaType type, MixInResolver r,
                                                        boolean forSerialization) {
      if (isDesugaredRecordClass(type.getRawClass())) {
        AnnotatedClass classDef = _resolveAnnotatedClass(config, type, r);
        AccessorNamingStrategy accNaming = new AndroidRecordNaming(config, classDef);
        return constructPropertyCollector(config, classDef, type, forSerialization, accNaming);
      }

      return super.collectProperties(config, type, r, forSerialization);
    }
  }

  @Override
  public void setupModule(SetupContext context) {
    super.setupModule(context);
    context.addValueInstantiators(AndroidRecordModule::findValueInstantiator);
    context.setClassIntrospector(new AndroidRecordClassIntrospector());
  }

  static boolean isDesugaredRecordClass(Class<?> raw) {
    return raw.getSuperclass() != null && raw.getSuperclass().getName().equals("com.android.tools.r8.RecordTag");
  }

  private static ValueInstantiator findValueInstantiator(DeserializationConfig config, BeanDescription beanDesc,
                                                         ValueInstantiator defaultInstantiator) {
    Class<?> raw = beanDesc.getType().getRawClass();
    if (! defaultInstantiator.canCreateFromObjectWith() && defaultInstantiator instanceof StdValueInstantiator
            && isDesugaredRecordClass(raw)) {
      Map<String, Type> components = getDesugaredRecordComponents(raw)
              .collect(Collectors.toMap(Field::getName, Field::getGenericType));
      boolean found = false;
      for (AnnotatedConstructor constructor: beanDesc.getConstructors()) {
        Parameter[] parameters = constructor.getAnnotated().getParameters();
        Map<String, Type> parameterTypes = Arrays.stream(parameters)
                .collect(Collectors.toMap(Parameter::getName, Parameter::getParameterizedType));
        if (! parameterTypes.equals(components)) {
          continue;
        }

        if (found) {
          throw new IllegalArgumentException(String.format(
                          "Multiple constructors match set of components for record %s", raw.getName()));
        }

        AnnotationIntrospector intro = config.getAnnotationIntrospector();
        SettableBeanProperty[] properties = new SettableBeanProperty[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
          AnnotatedParameter parameter = constructor.getParameter(i);
          JacksonInject.Value injectable = intro.findInjectableValue(parameter);
          PropertyName name = intro.findNameForDeserialization(parameter);
          if (name == null || name.isEmpty()) {
              name = PropertyName.construct(parameters[i].getName());
          }

          properties[i] = CreatorProperty.construct(name, parameter.getType(),
                  null, null, parameter.getAllAnnotations(), parameter, i, injectable, null);
        }

        AndroidRecordInstantiator instantiator = new AndroidRecordInstantiator((StdValueInstantiator) defaultInstantiator);
        instantiator.configureFromObjectSettings(null, null, null, null, constructor, properties);

        ClassUtil.checkAndFixAccess(constructor.getAnnotated(), false);
        found = true;

        defaultInstantiator = instantiator;
      }
    }

    return defaultInstantiator;
  }

  static Stream<Field> getDesugaredRecordComponents(Class<?> raw) {
    return Arrays.stream(raw.getDeclaredFields()).filter(field -> ! Modifier.isStatic(field.getModifiers()));
  }
}
