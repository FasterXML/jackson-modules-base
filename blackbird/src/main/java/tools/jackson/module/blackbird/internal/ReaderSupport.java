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
     * field store throws t: SettableBeanProperty._throwAsJacksonE itself,
     * called through a probe. A scalar arm no longer has its value, but the
     * message needs only the value's class (ClassUtil.classNameOf takes a
     * Class as is), which the property type fixes; a child arm passes its
     * value.
     */
    static Throwable storeException(Throwable t, Object childValue, SettableBeanProperty prop,
            JsonParser p) {
        Class<?> raw = prop.getType().getRawClass();
        Object value = raw.isPrimitive() ? ClassUtil.wrapperType(raw)
                : (raw == String.class) ? String.class : childValue;
        return PropertyProbe.storeException(prop, p, t, value);
    }

    // SettableBeanProperty._throwAsJacksonE is protected; the delegating
    // copy carries the property's name and type into this probe.
    private static final class PropertyProbe extends SettableBeanProperty.Delegating {
        private PropertyProbe(SettableBeanProperty src) {
            super(src);
        }

        @Override
        protected SettableBeanProperty withDelegate(SettableBeanProperty d) {
            return new PropertyProbe(d);
        }

        static Throwable storeException(SettableBeanProperty prop, JsonParser p, Throwable t,
                Object value) {
            try {
                new PropertyProbe(prop)._throwAsJacksonE(p, t, value);
            } catch (RuntimeException | Error e) {
                return e;
            }
            // _throwAsJacksonE always throws
            return new IllegalStateException(t);
        }
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
