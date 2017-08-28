package com.fasterxml.jackson.module.afterburner.util;


import java.util.List;

public class DynamicPropertyAccessorBase
{

    protected int _accessorCount = 0;

    protected DynamicPropertyAccessorBase() {
    }

    public final boolean isEmpty() {
        return (_accessorCount == 0);
    }
    
    /*
    /**********************************************************
    /* Helper methods, other
    /**********************************************************
     */

    protected <T> T _add(List<T> list, T value) {
        list.add(value);
        ++_accessorCount;
        return value;
    }

}
