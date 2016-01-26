package com.fasterxml.jackson.module.guice;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.binder.ScopedBindingBuilder;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

/**
 */
public class ObjectMapperModule implements com.google.inject.Module
{
  private final List<Module> modulesToAdd = new ArrayList<Module>();
  private final List<Key<? extends Module>> modulesToInject = new ArrayList<Key<? extends Module>>();
  private final Key<ObjectMapper> objectMapperKey;

  private Class<? extends Annotation> scope = null;

  public ObjectMapperModule()
  {
    this(Key.get(ObjectMapper.class));
  }

  public ObjectMapperModule(
      Class<? extends Annotation> annotation
  )
  {
    this(Key.get(ObjectMapper.class, annotation));
  }

  public ObjectMapperModule(
      Annotation annotation
  )
  {
    this(Key.get(ObjectMapper.class, annotation));
  }

  public ObjectMapperModule(
      Key<ObjectMapper> objectMapperKey
  )
  {
    this.objectMapperKey = objectMapperKey;
  }

  public ObjectMapperModule in(Class<? extends Annotation> scopeAnnotation)
  {
    scope = scopeAnnotation;
    return this;
  }

  public ObjectMapperModule registerModule(Module module)
  {
    modulesToAdd.add(module);
    return this;
  }

  public ObjectMapperModule registerModule(Class<? extends Module> clazz)
  {
    return registerModule(Key.get(clazz));
  }

  public ObjectMapperModule registerModule(Class<? extends Module> clazz, Class<? extends Annotation> annotation)
  {
    return registerModule(Key.get(clazz, annotation));
  }

  public ObjectMapperModule registerModule(Class<? extends Module> clazz, Annotation annotation)
  {
    return registerModule(Key.get(clazz, annotation));
  }

  public ObjectMapperModule registerModule(Key<? extends Module> key)
  {
    modulesToInject.add(key);
    return this;
  }

  @Override
  public void configure(Binder binder)
  {
    final ScopedBindingBuilder builder = binder.bind(objectMapperKey)
                                               .toProvider(new ObjectMapperProvider(modulesToInject, modulesToAdd));

    if (scope != null) {
      builder.in(scope);
    }
  }

  private static class ObjectMapperProvider implements Provider<ObjectMapper>
  {
    private final List<Key<? extends Module>> modulesToInject;
    private final List<Module> modulesToAdd;

    private final List<Provider<? extends Module>> providedModules;
    private Injector injector;

    public ObjectMapperProvider(
        List<Key<? extends Module>> modulesToInject,
        List<Module> modulesToAdd
    )
    {
      this.modulesToInject = modulesToInject;
      this.modulesToAdd = modulesToAdd;

      this.providedModules = new ArrayList<Provider<? extends Module>>();
    }

    @Inject
    public void configure(Injector injector) {
      this.injector = injector;
      for (Key<? extends Module> key : modulesToInject) {
        providedModules.add(injector.getProvider(key));
      }
    }

    @Override
    public ObjectMapper get()
    {
      final ObjectMapper mapper = new ObjectMapper();
      mapper.registerModules(modulesToAdd);
      for (Provider<? extends Module> provider : providedModules) {
        mapper.registerModule(provider.get());
      }

      final GuiceAnnotationIntrospector guiceIntrospector = new GuiceAnnotationIntrospector();
      mapper.setInjectableValues(new GuiceInjectableValues(injector));
      mapper.setAnnotationIntrospectors(
          new AnnotationIntrospectorPair(
              guiceIntrospector, mapper.getSerializationConfig().getAnnotationIntrospector()
          ),
          new AnnotationIntrospectorPair(
              guiceIntrospector, mapper.getDeserializationConfig().getAnnotationIntrospector()
          )
      );
      return mapper;
    }
  }
}
