package tools.jackson.module.blackbird.deser;

import java.lang.reflect.Modifier;

import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.deser.bean.BeanDeserializer;
import tools.jackson.databind.deser.bean.BuilderBasedDeserializer;
import tools.jackson.databind.introspect.BeanPropertyDefinition;
import tools.jackson.module.blackbird.codegen.CodegenDebug;
import tools.jackson.module.blackbird.codegen.CodegenFallbacks;

/**
 * Wraps eligible stock bean deserializers in a placeholder that generates a
 * per-bean codec at resolve time (ClassFile API hidden class). Every gate that
 * fails leaves the stock deserializer in place, so behavior never changes for
 * beans the generator does not fully understand. Most gates run at resolve
 * time, on the resolved stock deserializer (see BBReaderFactory); this
 * modifier only filters out what cannot become a codec at all.
 */
public class BBDeserializerModifier extends ValueDeserializerModifier
{
    private static final long serialVersionUID = 1L;

    // Member access rides databind's own fixAccess (see MemberHandles), so
    // no lookup is needed.
    public BBDeserializerModifier() { }

    @Override
    public ValueDeserializer<?> modifyDeserializer(DeserializationConfig config,
            BeanDescription.Supplier beanDescRef, ValueDeserializer<?> deserializer)
    {
        // Gate failures of any kind leave the stock deserializer in place. The
        // reflective gates can throw for exotic classes (a bean from a foreign
        // classloader with inconsistent InnerClasses metadata raises
        // IncompatibleClassChangeError from getEnclosingClass), and an
        // acceleration modifier must never break a bean stock databind handles.
        try {
            return doModify(config, beanDescRef, deserializer);
        } catch (RuntimeException | LinkageError e) {
            CodegenFallbacks.gateFailure(beanDescRef.getBeanClass(), e);
            return deserializer;
        }
    }

    private ValueDeserializer<?> doModify(DeserializationConfig config,
            BeanDescription.Supplier beanDescRef, ValueDeserializer<?> deserializer)
    {
        if (deserializer.getClass() != BeanDeserializer.class
                && deserializer.getClass() != BuilderBasedDeserializer.class) {
            return skip(beanDescRef, deserializer, "not a stock bean deserializer");
        }
        if (Boolean.TRUE.equals(config.getDefaultMergeable())) {
            return skip(beanDescRef, deserializer, "mapper default mergeable");
        }
        BeanDescription beanDesc = beanDescRef.get();
        Class<?> beanClass = beanDesc.getBeanClass();
        // Non-static inner classes construct against an enclosing instance,
        // which the generated loop does not model. The static check runs
        // first: for a static member class redefined in a foreign classloader,
        // getEnclosingClass raises IncompatibleClassChangeError, and such
        // beans accelerate.
        if (!Modifier.isStatic(beanClass.getModifiers())
                && beanClass.getEnclosingClass() != null) {
            return skip(beanDescRef, deserializer, "non-static inner class");
        }
        if (deserializer.getClass() == BeanDeserializer.class && !beanClass.isRecord()
                && Modifier.isAbstract(beanClass.getModifiers())) {
            return skip(beanDescRef, deserializer, "abstract");
        }
        // Whether the bean or any property declares @JsonView explicitly: the
        // resolved properties cannot tell a declared view from the empty view
        // set that disabled DEFAULT_VIEW_INCLUSION forces onto unannotated
        // properties, so the property definitions decide here.
        boolean declaresViews = config.getAnnotationIntrospector()
                .findViews(config, beanDesc.getClassInfo()) != null;
        for (BeanPropertyDefinition def : beanDesc.findProperties()) {
            if (def.findViews() != null) {
                declaresViews = true;
            }
            if (def.getPrimaryMember() != null
                    && config.getAnnotationIntrospector()
                            .findUnwrappingNameTransformer(config, def.getPrimaryMember()) != null) {
                return skip(beanDescRef, deserializer, "unwrapped property");
            }
            if (def.getMetadata() != null && def.getMetadata().getMergeInfo() != null) {
                return skip(beanDescRef, deserializer, "property merge");
            }
        }
        if (deserializer instanceof BuilderBasedDeserializer builder) {
            return new BBBuilderPlaceholder(builder, declaresViews);
        }
        return new BBReaderPlaceholder((BeanDeserializer) deserializer, declaresViews);
    }

    private static ValueDeserializer<?> skip(BeanDescription.Supplier beanDescRef,
            ValueDeserializer<?> deserializer, String reason) {
        CodegenDebug.logSkip("reader", beanDescRef.getBeanClass(), reason);
        return deserializer;
    }
}
