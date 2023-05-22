package tools.jackson.module.guice7;

import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.InjectableValues;
import com.google.inject.Injector;
import com.google.inject.Key;

public class GuiceInjectableValues extends InjectableValues
{
  private final Injector injector;

  public GuiceInjectableValues(Injector injector) {this.injector = injector;}

  @Override
  public Object findInjectableValue(
      Object valueId, DeserializationContext ctxt, BeanProperty forProperty, Object beanInstance
  )
  {
    return injector.getInstance((Key<?>) valueId);
  }

  @Override
  public InjectableValues snapshot() {
      // 22-May-2023, tatu: Not sure if and how this could work really...
      return this;
  }
}
