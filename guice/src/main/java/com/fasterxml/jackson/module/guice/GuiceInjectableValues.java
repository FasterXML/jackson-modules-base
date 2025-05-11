package com.fasterxml.jackson.module.guice;

import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.InjectableValues;
import com.google.inject.Injector;
import com.google.inject.Key;

public class GuiceInjectableValues extends InjectableValues
{
  private final Injector injector;

  public GuiceInjectableValues(Injector injector) {this.injector = injector;}

  @Deprecated // since 2.20
  @Override
  public Object findInjectableValue(
      Object valueId, DeserializationContext ctxt, BeanProperty forProperty, Object beanInstance)
  {
      return injector.getInstance((Key<?>) valueId);
  }

  @Override
  public Object findInjectableValue(DeserializationContext ctxt,
      Object valueId, BeanProperty forProperty, Object beanInstance,
      Boolean optional)
  {
      return injector.getInstance((Key<?>) valueId);
  }
}
