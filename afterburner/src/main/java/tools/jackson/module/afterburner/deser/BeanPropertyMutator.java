package tools.jackson.module.afterburner.deser;

import tools.jackson.databind.DeserializationContext;

/**
 * Abstract class that defines interface for implementations
 * that can be used proxy-like to change values of properties,
 * without using Reflection.
 */
public abstract class BeanPropertyMutator
{
    /*
    /********************************************************************** 
    /* Life-cycle methods
    /********************************************************************** 
     */

    /**
     * Default constructor used for creating a "blueprint" instance, from
     * which per-field/per-method instances specialize.
     */
    public BeanPropertyMutator() { }

    /*
    /********************************************************************** 
    /* Underlying methods sub-classes are to implement
    /********************************************************************** 
     */

    // !!! 04-Dec-2024, tatu: Wrt [databind#3561] we really SHOULD take in
    //   and pass first argument of type `DeserializationContext` here.
    //   But doing that would require changes to bytecode generation that
    //   are non-trivial, and Afterburner may be deprecated with 3.0 anyway
    //   so for now we just pass `null`
    
    public void intSetter(DeserializationContext ctxt, Object bean, int propertyIndex, int value) {
        throw new UnsupportedOperationException("No intSetters defined");
    }
    public void longSetter(DeserializationContext ctxt, Object bean, int propertyIndex, long value){
        throw new UnsupportedOperationException("No longSetters defined");
    }
    public void booleanSetter(DeserializationContext ctxt, Object bean, int propertyIndex, boolean value) {
        throw new UnsupportedOperationException("No booleanSetters defined");
    }
    public void stringSetter(DeserializationContext ctxt, Object bean, int propertyIndex, String value) {
        throw new UnsupportedOperationException("No stringSetters defined");
    }
    public void objectSetter(DeserializationContext ctxt, Object bean, int propertyIndex, Object value) {
        throw new UnsupportedOperationException("No objectSetters defined");
    }
    public void intField(DeserializationContext ctxt, Object bean, int propertyIndex, int value) {
        throw new UnsupportedOperationException("No intFields defined");
    }
    public void longField(DeserializationContext ctxt, Object bean, int propertyIndex, long value) {
        throw new UnsupportedOperationException("No longFields defined");
    }
    public void booleanField(DeserializationContext ctxt, Object bean, int propertyIndex, boolean value) {
        throw new UnsupportedOperationException("No booleanFields defined");
    }
    public void stringField(DeserializationContext ctxt, Object bean, int propertyIndex, String value) {
        throw new UnsupportedOperationException("No stringFields defined");
    }
    public void objectField(DeserializationContext ctxt, Object bean, int propertyIndex, Object value) {
        throw new UnsupportedOperationException("No objectFields defined");
    }
}
