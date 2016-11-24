package com.fasterxml.jackson.module.afterburner.deser;

import java.io.IOException;

import com.fasterxml.jackson.databind.deser.SettableBeanProperty;

/**
 * Fallback mutator used as replacement in case a "broken" mutator
 * (failure via couple of well-known indicators of broken generated
 * mutator) is encountered
 *
 * @since 2.9
 */
public final class DelegatingPropertyMutator
    extends BeanPropertyMutator
{
    protected final SettableBeanProperty _fallback;

    public DelegatingPropertyMutator(SettableBeanProperty prop) {
        _fallback = prop;
    }

    @Override
    public void intSetter(Object bean, int propertyIndex, int value) throws IOException {
        _fallback.set(bean, value);
    }
    @Override
    public void longSetter(Object bean, int propertyIndex, long value) throws IOException {
        _fallback.set(bean, value);
    }
    @Override
    public void booleanSetter(Object bean, int propertyIndex, boolean value) throws IOException {
        _fallback.set(bean, value);
    }
    @Override
    public void stringSetter(Object bean, int propertyIndex, String value) throws IOException {
        _fallback.set(bean, value);
    }
    @Override
    public void objectSetter(Object bean, int propertyIndex, Object value) throws IOException {
        _fallback.set(bean, value);
    }

    @Override
    public void intField(Object bean, int propertyIndex, int value) throws IOException {
        _fallback.set(bean, value);
    }
    @Override
    public void longField(Object bean, int propertyIndex, long value) throws IOException {
        _fallback.set(bean, value);
    }
    @Override
    public void booleanField(Object bean, int propertyIndex, boolean value) throws IOException {
        _fallback.set(bean, value);
    }
    @Override
    public void stringField(Object bean, int propertyIndex, String value) throws IOException {
        _fallback.set(bean, value);
    }
    @Override
    public void objectField(Object bean, int propertyIndex, Object value) throws IOException {
        _fallback.set(bean, value);
    }

    // // // Optimized
    
    @Override
    public void intField0(Object bean, int value) throws IOException {
        _fallback.set(bean, value);
    }
    @Override
    public void intField1(Object bean, int value) throws IOException {
        _fallback.set(bean, value);
    }
    @Override
    public void stringField0(Object bean, String value) throws IOException {
        _fallback.set(bean, value);
    }
    @Override
    public void stringField1(Object bean, String value) throws IOException {
        _fallback.set(bean, value);
    }

    @Override
    public void intSetter0(Object bean, int value) throws IOException {
        _fallback.set(bean, value);
    }
    @Override
    public void intSetter1(Object bean, int value) throws IOException {
        _fallback.set(bean, value);
    }
    @Override
    public void stringSetter0(Object bean, String value) throws IOException {
        _fallback.set(bean, value);
    }
    @Override
    public void stringSetter1(Object bean, String value) throws IOException {
        _fallback.set(bean, value);
    }
}
