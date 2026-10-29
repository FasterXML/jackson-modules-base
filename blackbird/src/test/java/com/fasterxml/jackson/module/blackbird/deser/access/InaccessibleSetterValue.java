package com.fasterxml.jackson.module.blackbird.deser.access;

public class InaccessibleSetterValue {
    public int calls;
    public Object value;

    public void setVoidValue(SetterValue value) {
        calls++;
        this.value = value;
    }

    public InaccessibleSetterValue setFluentValue(SetterValue value) {
        calls++;
        this.value = value;
        return this;
    }

    public static Object newValue() {
        return new SetterValue();
    }
}

class SetterValue { }
