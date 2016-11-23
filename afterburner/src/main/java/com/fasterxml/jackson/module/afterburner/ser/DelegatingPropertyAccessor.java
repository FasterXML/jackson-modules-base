package com.fasterxml.jackson.module.afterburner.ser;

import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;

/**
 * Fallback accessor used as replacement in case a "broken" accessor
 * (failure via couple of well-known indicators of broken generated
 * code) is encountered
 *
 * @since 2.9
 */
public class DelegatingPropertyAccessor
    extends BeanPropertyAccessor
{
    protected final BeanPropertyWriter _fallback;

    public DelegatingPropertyAccessor(BeanPropertyWriter prop) {
        _fallback = prop;
    }

    @Override
    public boolean booleanGetter(Object bean, int property) throws Exception {
        return ((Boolean) _fallback.get(bean)).booleanValue();
    }
    @Override
    public int intGetter(Object bean, int property) throws Exception {
        return ((Integer) _fallback.get(bean)).intValue();
    }
    @Override
    public long longGetter(Object bean, int property) throws Exception {
        return ((Long) _fallback.get(bean)).longValue();
    }
    @Override
    public String stringGetter(Object bean, int property) throws Exception {
        return (String) _fallback.get(bean);
    }
    @Override
    public Object objectGetter(Object bean, int property) throws Exception {
        return _fallback.get(bean);
    }

    @Override
    public boolean booleanField(Object bean, int property) throws Exception {
        return ((Boolean) _fallback.get(bean)).booleanValue();
    }
    @Override
    public int intField(Object bean, int property) throws Exception {
        return ((Integer) _fallback.get(bean)).intValue();
    }
    @Override
    public long longField(Object bean, int property) throws Exception {
        return ((Long) _fallback.get(bean)).longValue();
    }
    @Override
    public String stringField(Object bean, int property) throws Exception {
        return (String) _fallback.get(bean);
    }
    @Override
    public Object objectField(Object bean, int property) throws Exception {
        return _fallback.get(bean);
    }
}
