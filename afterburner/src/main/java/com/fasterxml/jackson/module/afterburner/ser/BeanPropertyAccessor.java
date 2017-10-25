package com.fasterxml.jackson.module.afterburner.ser;

/**
 * Abstract class that defines interface for implementations
 * that can be used for proxy-like access without using
 * Reflection.
 */
public abstract class BeanPropertyAccessor
{
    public boolean booleanGetter(Object bean, int property) {
        throw new UnsupportedOperationException("No booleanGetters defined");
    }
    public int intGetter(Object bean, int property) {
        throw new UnsupportedOperationException("No intGetters defined");
    }
    public long longGetter(Object bean, int property) {
        throw new UnsupportedOperationException("No longGetters defined");
    }
    public String stringGetter(Object bean, int property) {
        throw new UnsupportedOperationException("No stringGetters defined");
    }
    public Object objectGetter(Object bean, int property) {
        throw new UnsupportedOperationException("No objectGetters defined");
    }

    public boolean booleanField(Object bean, int property) {
        throw new UnsupportedOperationException("No booleanFields defined");
    }
    public int intField(Object bean, int property) {
        throw new UnsupportedOperationException("No intFields defined");
    }
    public long longField(Object bean, int property) {
        throw new UnsupportedOperationException("No longFields defined");
    }
    public String stringField(Object bean, int property) {
        throw new UnsupportedOperationException("No stringFields defined");
    }
    public Object objectField(Object bean, int property) {
        throw new UnsupportedOperationException("No objectFields defined");
    }
}
