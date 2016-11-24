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
    /* Underlying methods sub-classes are to implement; general
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

    /*
    /********************************************************************** 
    /* Underlying methods sub-classes are to implement; optimized
    /********************************************************************** 
     */

    public void intField0(Object bean, int value) throws IOException {
        throw new UnsupportedOperationException("No intField0 defined");
    }
    public void intField1(Object bean, int value) throws IOException {
        throw new UnsupportedOperationException("No intField1 defined");
    }
    public void stringField0(Object bean, String value) throws IOException {
        throw new UnsupportedOperationException("No stringField0 defined");
    }
    public void stringField1(Object bean, String value) throws IOException {
        throw new UnsupportedOperationException("No stringField1 defined");
    }

    public void intSetter0(Object bean, int value) throws IOException {
        throw new UnsupportedOperationException("No intSetter0 defined");
    }
    public void intSetter1(Object bean, int value) throws IOException {
        throw new UnsupportedOperationException("No intSetter1 defined");
    }
    public void intSetter2(Object bean, int value) throws IOException {
        throw new UnsupportedOperationException("No intSetter2 defined");
    }
    public void intSetter3(Object bean, int value) throws IOException {
        throw new UnsupportedOperationException("No intSetter3 defined");
    }

    public void longSetter0(Object bean, long value) throws IOException {
        throw new UnsupportedOperationException("No longSetter0 defined");
    }
    public void longSetter1(Object bean, long value) throws IOException {
        throw new UnsupportedOperationException("No longSetter1 defined");
    }

    public void stringSetter0(Object bean, String value) throws IOException {
        throw new UnsupportedOperationException("No stringSetter0 defined");
    }
    public void stringSetter1(Object bean, String value) throws IOException {
        throw new UnsupportedOperationException("No stringSetter1 defined");
    }
    public void stringSetter2(Object bean, String value) throws IOException {
        throw new UnsupportedOperationException("No stringSetter2 defined");
    }
    public void stringSetter3(Object bean, String value) throws IOException {
        throw new UnsupportedOperationException("No stringSetter3 defined");
    }

    public void objectSetter0(Object bean, Object value) throws IOException {
        throw new UnsupportedOperationException("No objectSetter0 defined");
    }
    public void objectSetter1(Object bean, Object value) throws IOException {
        throw new UnsupportedOperationException("No objectSetter1 defined");
    }
}
