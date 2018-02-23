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

  @Override
  public Object findInjectableValue(
      Object valueId, DeserializationContext ctxt, BeanProperty forProperty, Object beanInstance
  )
  {
    return injector.getInstance((Key<?>) valueId);
  }

  @Override
  public InjectableValues snapshot() {
      // 23-Feb-2018, tatu: Not sure if and how this could work really...
      return this;
  }
}
