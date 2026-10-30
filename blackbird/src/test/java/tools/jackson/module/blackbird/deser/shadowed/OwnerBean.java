package tools.jackson.module.blackbird.deser.shadowed;

import tools.jackson.module.blackbird.deser.shadowed.owner.OwnerBridge;

// in another package than the protected setter's declaring class, so its handle's
// receiver is narrowed to this class
public final class OwnerBean extends OwnerBridge
{
    public int value() { return value; }
}
