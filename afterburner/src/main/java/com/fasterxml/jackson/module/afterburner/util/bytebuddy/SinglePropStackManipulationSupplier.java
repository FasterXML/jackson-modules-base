package com.fasterxml.jackson.module.afterburner.util.bytebuddy;

import net.bytebuddy.implementation.bytecode.StackManipulation;

public interface SinglePropStackManipulationSupplier<T> {

    StackManipulation supply(T prop);
}
