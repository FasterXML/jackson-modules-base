package tools.jackson.module.afterburner.deser;

import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.SettableBeanProperty;

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
    public void intSetter(DeserializationContext ctxt, Object bean, int propertyIndex, int value) {
        _fallback.set(ctxt, bean, value);
    }
    @Override
    public void longSetter(DeserializationContext ctxt, Object bean, int propertyIndex, long value) {
        _fallback.set(ctxt, bean, value);
    }
    @Override
    public void booleanSetter(DeserializationContext ctxt, Object bean, int propertyIndex, boolean value) {
        _fallback.set(ctxt, bean, value);
    }
    @Override
    public void stringSetter(DeserializationContext ctxt, Object bean, int propertyIndex, String value) {
        _fallback.set(ctxt, bean, value);
    }
    @Override
    public void objectSetter(DeserializationContext ctxt, Object bean, int propertyIndex, Object value) {
        _fallback.set(ctxt, bean, value);
    }

    @Override
    public void intField(DeserializationContext ctxt, Object bean, int propertyIndex, int value) {
        _fallback.set(ctxt, bean, value);
    }
    @Override
    public void longField(DeserializationContext ctxt, Object bean, int propertyIndex, long value) {
        _fallback.set(ctxt, bean, value);
    }
    @Override
    public void booleanField(DeserializationContext ctxt, Object bean, int propertyIndex, boolean value) {
        _fallback.set(ctxt, bean, value);
    }
    @Override
    public void stringField(DeserializationContext ctxt, Object bean, int propertyIndex, String value) {
        _fallback.set(ctxt, bean, value);
    }
    @Override
    public void objectField(DeserializationContext ctxt, Object bean, int propertyIndex, Object value) {
        _fallback.set(ctxt, bean, value);
    }
}
