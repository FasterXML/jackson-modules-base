package com.fasterxml.jackson.module.androidrecord;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.CreatorProperty;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.deser.ValueInstantiator;
import com.fasterxml.jackson.databind.deser.std.StdValueInstantiator;
import com.fasterxml.jackson.databind.introspect.AnnotatedParameter;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;

public class AndroidRecordInstantiator extends StdValueInstantiator {
  protected AndroidRecordInstantiator(StdValueInstantiator src) {
    super(src);
  }

  public ValueInstantiator createContextual(DeserializationContext ctxt, BeanDescription beanDesc) throws JsonMappingException {
    boolean wasChanged = false;
    SettableBeanProperty[] newCtorArgs = new SettableBeanProperty[_constructorArguments.length];
    for (int i = 0; i < _constructorArguments.length; i++) {
      SettableBeanProperty prop = _constructorArguments[i];
      if (!prop.hasValueTypeDeserializer()) {
        TypeDeserializer typeDeserializer = ctxt.getFactory().findTypeDeserializer(ctxt.getConfig(), prop.getType());
        if (typeDeserializer != null) {
          prop = CreatorProperty.construct(
                  prop.getFullName(),
                  prop.getType(),
                  prop.getWrapperName(),
                  typeDeserializer,
                  prop.getMember().getAllAnnotations(),
                  (AnnotatedParameter) prop.getMember(),
                  prop.getCreatorIndex(),
                  ctxt.getAnnotationIntrospector().findInjectableValue(prop.getMember()),
                  prop.getMetadata()
          );
          wasChanged = true;
        }
      }
      newCtorArgs[i] = prop;
    }


    if (wasChanged) {
      AndroidRecordInstantiator newInstantiator = new AndroidRecordInstantiator(this);
      newInstantiator._constructorArguments = newCtorArgs;
      return newInstantiator;
    } else {
      return this;
    }
  }
}
