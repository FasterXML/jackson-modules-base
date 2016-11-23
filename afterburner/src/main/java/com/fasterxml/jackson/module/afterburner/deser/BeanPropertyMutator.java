package com.fasterxml.jackson.module.afterburner.deser;

import java.io.IOException;

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

    public void intSetter(Object bean, int propertyIndex, int value) throws IOException {
        throw new UnsupportedOperationException("No intSetters defined");
    }
    public void longSetter(Object bean, int propertyIndex, long value) throws IOException {
        throw new UnsupportedOperationException("No longSetters defined");
    }
    public void booleanSetter(Object bean, int propertyIndex, boolean value) throws IOException {
        throw new UnsupportedOperationException("No booleanSetters defined");
    }
    public void stringSetter(Object bean, int propertyIndex, String value) throws IOException {
        throw new UnsupportedOperationException("No stringSetters defined");
    }
    public void objectSetter(Object bean, int propertyIndex, Object value) throws IOException {
        throw new UnsupportedOperationException("No objectSetters defined");
    }
    public void intField(Object bean, int propertyIndex, int value) throws IOException {
        throw new UnsupportedOperationException("No intFields defined");
    }
    public void longField(Object bean, int propertyIndex, long value) throws IOException {
        throw new UnsupportedOperationException("No longFields defined");
    }
    public void booleanField(Object bean, int propertyIndex, boolean value) throws IOException {
        throw new UnsupportedOperationException("No booleanFields defined");
    }
    public void stringField(Object bean, int propertyIndex, String value) throws IOException {
        throw new UnsupportedOperationException("No stringFields defined");
    }
    public void objectField(Object bean, int propertyIndex, Object value) throws IOException {
        throw new UnsupportedOperationException("No objectFields defined");
    }
}
