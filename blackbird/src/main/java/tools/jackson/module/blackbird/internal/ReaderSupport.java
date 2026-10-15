package tools.jackson.module.blackbird.internal;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.deser.std.StdValueInstantiator;
import tools.jackson.databind.util.ClassUtil;

/**
 * Cold-path helpers shared by the generated reader bases: each reproduces
 * what a stock class does in the same situation, where that code is not
 * reachable through inheritance.
 */
final class ReaderSupport
{
    private ReaderSupport() {}

    /**
     * The exception that the stock MethodProperty throws when its setter or
     * field store throws t (SettableBeanProperty._throwAsJacksonE). A scalar
     * arm no longer has its value, but the message needs only the value's
     * class, which the property type fixes; a child arm passes its value.
     */
    static Throwable storeException(Throwable t, Object childValue, SettableBeanProperty prop,
            JsonParser p) {
        if (t instanceof IllegalArgumentException) {
            Class<?> raw = prop.getType().getRawClass();
            Object value = raw.isPrimitive() ? ClassUtil.wrapperType(raw)
                    : (raw == String.class) ? String.class : childValue;
            StringBuilder msg = new StringBuilder("Problem deserializing property '")
                    .append(prop.getName())
                    .append("' (expected type: ")
                    .append(prop.getType())
                    .append("; actual type: ")
                    .append(ClassUtil.classNameOf(value)).append(")");
            String origMsg = ClassUtil.exceptionMessage(t);
            if (origMsg != null) {
                msg.append(", problem: ").append(origMsg);
            } else {
                msg.append(" (no error message provided)");
            }
            return DatabindException.from(p, msg.toString(), t);
        }
        if (t instanceof RuntimeException) {
            return t;
        }
        return DatabindException.from(p, ClassUtil.exceptionMessage(t), t);
    }

    /**
     * What StdValueInstantiator does with an exception from the creator it
     * calls (the instantiator must be exactly that class): problem handlers
     * may supply a value, otherwise the wrapped exception is thrown.
     */
    static Object creatorProblem(StdValueInstantiator inst, DeserializationContext ctxt,
            Object[] args, Throwable t) {
        return ctxt.handleInstantiationProblem(inst.getValueClass(), args,
                InstantiatorProbe.rewrapCtorProblem(inst, ctxt, t));
    }

    // StdValueInstantiator.rewrapCtorProblem is protected; the copy
    // constructor carries the instantiator's state into this probe.
    private static final class InstantiatorProbe extends StdValueInstantiator {
        private InstantiatorProbe(StdValueInstantiator src) {
            super(src);
        }

        static DatabindException rewrapCtorProblem(StdValueInstantiator src,
                DeserializationContext ctxt, Throwable t) {
            return new InstantiatorProbe(src).rewrapCtorProblem(ctxt, t);
        }
    }
}
