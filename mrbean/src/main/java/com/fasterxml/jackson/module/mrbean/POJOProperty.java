/*
* Copyright (c) FasterXML, LLC.
* Licensed to the Apache Software Foundation (ASF)
* under one or more contributor license agreements;
* and to You under the Apache License, Version 2.0.
*/

package com.fasterxml.jackson.module.mrbean;

import java.lang.reflect.Method;

import com.fasterxml.jackson.databind.introspect.TypeResolutionContext;
import com.fasterxml.jackson.module.mrbean.BeanBuilder.TypeDescription;

/**
 * Bean that contains information about a single logical
 * POJO property. Properties consist of a getter and/or setter,
 * and are used to generate getter and setter methods and matching
 * backing field.
 */
public class POJOProperty
{
    protected final String _name;
    protected final String _fieldName;

    /**
     * Class in which setter/getter was declared, needed for resolving
     * generic types.
     */
    protected final TypeResolutionContext _context;
    
    protected Method _getter;
    protected Method _setter;

    public POJOProperty(TypeResolutionContext ctxt, String name)
    {
        _name = name;
        _context = ctxt;
        /* 06-Mar-2015, tatu: We used to use '_' prefix, but that leads to issues
         *   like [#20]; as well as prevents expected use without explicit setter.
         */
        // Let's just prefix field name with single underscore for fun...
        _fieldName = name;
    }

    public String getName() { return _name; }
    
    public void setGetter(Method m) { _getter = m; }
    public void setSetter(Method m) { _setter = m; }
    
    public Method getGetter() { return _getter; }
    public Method getSetter() { return _setter; }

    public String getFieldName() {
        return _fieldName;
    }

    /*
    private static boolean isConcrete(Method m)
    {
        return m.getModifiers()
    }
    */
    
    public boolean hasConcreteGetter() {
        return (_getter != null) && BeanUtil.isConcrete(_getter);
    }

    public boolean hasConcreteSetter() {
        return (_setter != null) && BeanUtil.isConcrete(_setter);
    }

    private TypeDescription getterType() {
        return new TypeDescription(_context.resolveType(_getter.getGenericReturnType()));
    }

    private TypeDescription setterType() {
        return new TypeDescription(_context.resolveType((_setter.getGenericParameterTypes()[0])));
    }
    
    public TypeDescription selectType()
    {
        // First: if only know setter, or getter, use that one:
        if (_getter == null) {
            return setterType();
        }
        if (_setter == null) {
            return getterType();
        }
        /* Otherwise must ensure they are compatible, choose more specific
         * (most often setter - type)
         */
        TypeDescription st = setterType();
        TypeDescription gt = getterType();
        TypeDescription specificType = TypeDescription.moreSpecificType(st, gt);
        if (specificType == null) { // incompatible...
            throw new IllegalArgumentException("Invalid property '"+getName()
                    +"': incompatible types for getter/setter ("
                    +gt+" vs "+st+")");

        }
        return specificType;
    }
}
