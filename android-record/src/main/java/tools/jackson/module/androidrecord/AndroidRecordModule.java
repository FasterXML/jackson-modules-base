package tools.jackson.module.androidrecord;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JacksonInject;

import tools.jackson.databind.*;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.deser.CreatorProperty;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.deser.ValueInstantiator;
import tools.jackson.databind.deser.ValueInstantiators;
import tools.jackson.databind.deser.std.StdValueInstantiator;
import tools.jackson.databind.introspect.*;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.util.ClassUtil;

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
 * An attempt was made to make this module as consistent with Jackson's built-in support for records as possible,
 * but gaps exist when using some of Jackson's advanced mapping features.
 *
 * <p>
 * Note: this module is a no-op when no Android-desugared records are being (de)serialized,
 * so it is safe to use in code shared between Android and non-Android platforms.
 *
 * <p>
 * Note: the canonical record constructor is found through matching of parameter names and types with fields.
 * Therefore, this module doesn't allow a deserialized desugared record to have a custom constructor
 * with the same set of parameter names and types as the canonical one.
 *
 * @author Eran Leshem
 **/
public class AndroidRecordModule extends SimpleModule
{
  private static final long serialVersionUID = 1L;

  static final class AndroidRecordNaming
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

      public AndroidRecordClassIntrospector(DeserializationConfig config) {
          super(config);
      }

      @Override
      protected POJOPropertiesCollector collectProperties(JavaType type, AnnotatedClass classDef,
            boolean forSerialization, String mutatorPrefix)
      {
          if (isDesugaredRecordClass(type.getRawClass())) {
            AccessorNamingStrategy accNaming = new AndroidRecordNaming(_config, classDef);
            return constructPropertyCollector(type, classDef, forSerialization, accNaming);
          }
          return super.collectProperties(type, classDef, forSerialization, mutatorPrefix);
      }
  }

  @Override
  public void setupModule(SetupContext context) {
    super.setupModule(context);
    context.addValueInstantiators(new AndroidValueInstantiators());
    context.setClassIntrospector(new AndroidRecordClassIntrospector());
  }

  static boolean isDesugaredRecordClass(Class<?> raw) {
    return raw.getSuperclass() != null && raw.getSuperclass().getName().equals("com.android.tools.r8.RecordTag");
  }

  static Stream<Field> getDesugaredRecordComponents(Class<?> raw) {
      return Arrays.stream(raw.getDeclaredFields()).filter(field -> ! Modifier.isStatic(field.getModifiers()));
  }
  
  static class AndroidValueInstantiators
      extends ValueInstantiators.Base
  {
      @Override
      public ValueInstantiator modifyValueInstantiator(DeserializationConfig config, BeanDescription beanDesc,
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
              JacksonInject.Value injectable = intro.findInjectableValue(config, parameter);
              PropertyName name = intro.findNameForDeserialization(config, parameter);
              if (name == null || name.isEmpty()) {
                  name = PropertyName.construct(parameters[i].getName());
              }
    
              properties[i] = CreatorProperty.construct(name, parameter.getType(),
                      null, null, parameter.getAllAnnotations(), parameter, i, injectable, null);
            }
    
            ((StdValueInstantiator) defaultInstantiator).configureFromObjectSettings(null, null, null, null,
                    constructor, properties);
            ClassUtil.checkAndFixAccess(constructor.getAnnotated(), false);
            found = true;
          }
        }
        return defaultInstantiator;
      }
  }
}
