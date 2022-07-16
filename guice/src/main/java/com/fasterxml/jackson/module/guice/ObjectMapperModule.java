package com.fasterxml.jackson.module.guice;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.AnnotationIntrospector;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.MapperBuilder;
import tools.jackson.databind.introspect.AnnotationIntrospectorPair;
import tools.jackson.databind.introspect.JacksonAnnotationIntrospector;
import tools.jackson.databind.json.JsonMapper;
import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.binder.ScopedBindingBuilder;

public class ObjectMapperModule implements com.google.inject.Module
{
  private final List<JacksonModule> modulesToAdd = new ArrayList<JacksonModule>();
  private final List<Key<? extends JacksonModule>> modulesToInject = new ArrayList<Key<? extends JacksonModule>>();
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

  public ObjectMapperModule registerModule(JacksonModule module)
  {
    modulesToAdd.add(module);
    return this;
  }

  public ObjectMapperModule registerModule(Class<? extends JacksonModule> clazz)
  {
    return registerModule(Key.get(clazz));
  }

  public ObjectMapperModule registerModule(Class<? extends JacksonModule> clazz, Class<? extends Annotation> annotation)
  {
    return registerModule(Key.get(clazz, annotation));
  }

  public ObjectMapperModule registerModule(Class<? extends JacksonModule> clazz, Annotation annotation)
  {
    return registerModule(Key.get(clazz, annotation));
  }

  public ObjectMapperModule registerModule(Key<? extends JacksonModule> key)
  {
    modulesToInject.add(key);
    return this;
  }

  /**
   * @param m ObjectMapper to use for newly constructed module
   */
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
    private final List<Key<? extends JacksonModule>> modulesToInject;
    private final List<JacksonModule> modulesToAdd;

    private final List<Provider<? extends JacksonModule>> providedModules;
    private Injector injector;
    private final ObjectMapper objectMapper;

    public ObjectMapperProvider(List<Key<? extends JacksonModule>> modulesToInject,
        List<JacksonModule> modulesToAdd, ObjectMapper mapper)
    {
      this.modulesToInject = modulesToInject;
      this.modulesToAdd = modulesToAdd;
      objectMapper = mapper;
      this.providedModules = new ArrayList<Provider<? extends JacksonModule>>();
    }

    @Inject
    public void configure(Injector inj) {
      injector = inj;
      for (Key<? extends JacksonModule> key : modulesToInject) {
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
            MapperBuilder<?,?> builder = JsonMapper.builder()
                    .injectableValues(new GuiceInjectableValues(injector))
                    .annotationIntrospector(new AnnotationIntrospectorPair(guiceIntrospector, defaultAI))
                    .addModules(modulesToAdd);
            for (Provider<? extends JacksonModule> provider : providedModules) {
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
