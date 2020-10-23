package com.fasterxml.jackson.module.blackbird.deser;

@FunctionalInterface
public interface ObjBooleanConsumer {
    void accept(Object bean, boolean value);
}
