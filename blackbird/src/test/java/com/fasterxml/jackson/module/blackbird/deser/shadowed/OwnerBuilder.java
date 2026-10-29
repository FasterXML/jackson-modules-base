package com.fasterxml.jackson.module.blackbird.deser.shadowed;

import java.lang.invoke.MethodHandles;

import com.fasterxml.jackson.module.blackbird.deser.shadowed.owner.OwnerBridge;

// in another package than the protected setter's declaring class, so its handle's
// receiver is narrowed to this class
public final class OwnerBuilder extends OwnerBridge
{
    // so Blackbird can be given a lookup in this class, as for protected setters it needs
    public static MethodHandles.Lookup lookup() {
        return MethodHandles.lookup();
    }

    public OwnerBuilt build() {
        return new OwnerBuilt(value);
    }
}
