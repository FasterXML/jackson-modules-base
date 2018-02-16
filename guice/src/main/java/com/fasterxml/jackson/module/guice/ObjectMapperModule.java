package com.fasterxml.jackson.module.guice;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.MapperBuilder;
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.binder.ScopedBindingBuilder;

public class ObjectMapperModule implements com.google.inject.Module
{
  private final List<Module> modulesToAdd = new ArrayList<Module>();
  private final List<Key<? extends Module>> modulesToInject = new ArrayList<Key<? extends Module>>();
  private final Key<ObjectMapper> objectMapperKey;

  private ObjectMapper objectMapper;

  private Class<? extends Annotation> scope = null;

  public ObjectMapperModule()
  {
    this(Key.get(ObjectMapper.class));
  }

  public ObjectMapperModule(Class<? extends Annotation> annotation)
  {
    this(Key.get(ObjectMapper.class, annotation));
  }

  public ObjectMapperModule(Annotation annotation)
  {
    this(Key.get(ObjectMapper.class, annotation));
  }

  public ObjectMapperModule(Key<ObjectMapper> objectMapperKey)
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

  public ObjectMapperModule withObjectMapper(ObjectMapper m)
  {
    objectMapper = m;
    return this;
  }

  @Override
  public void configure(Binder binder)
  {
    final ScopedBindingBuilder builder = binder.bind(objectMapperKey)
            .toProvider(new ObjectMapperProvider(modulesToInject, modulesToAdd, objectMapper));

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
    private final ObjectMapper objectMapper;

    public ObjectMapperProvider(List<Key<? extends Module>> modulesToInject,
        List<Module> modulesToAdd, ObjectMapper mapper)
    {
      this.modulesToInject = modulesToInject;
      this.modulesToAdd = modulesToAdd;
      objectMapper = mapper;
      this.providedModules = new ArrayList<Provider<? extends Module>>();
    }

    @Inject
    public void configure(Injector inj) {
      injector = inj;
      for (Key<? extends Module> key : modulesToInject) {
        providedModules.add(injector.getProvider(key));
      }
    }

    @Override
    public ObjectMapper get()
    {
        ObjectMapper mapper = objectMapper;
        if (mapper == null) {
            final GuiceAnnotationIntrospector guiceIntrospector = new GuiceAnnotationIntrospector();
            AnnotationIntrospector defaultAI = new JacksonAnnotationIntrospector();
            MapperBuilder<?,?> builder = ObjectMapper.builder()
                    .injectableValues(new GuiceInjectableValues(injector))
                    .annotationIntrospector(new AnnotationIntrospectorPair(guiceIntrospector, defaultAI))
                    .addModules(modulesToAdd);
            for (Provider<? extends Module> provider : providedModules) {
                builder = builder.addModule(provider.get());
            }
            mapper = builder.build();

          /*
      } else {
            // 05-Feb-2017, tatu: _Should_ be fine, considering instances are now (3.0) truly immutable.
          //    But if this turns out to be problematic, may need to consider addition of `copy()`
          //    back in databind
          mapper = mapper.copy();
          */
      }
      return mapper;
    }
  }
}
