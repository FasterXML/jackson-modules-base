package tools.jackson.module.blackbird.deser;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;

import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.PropertyName;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.deser.BeanDeserializerBuilder;
import tools.jackson.databind.deser.bean.BeanDeserializer;
import tools.jackson.databind.deser.bean.BeanDeserializerBase;
import tools.jackson.databind.deser.bean.BuilderBasedDeserializer;
import tools.jackson.databind.introspect.AnnotatedMethod;
import tools.jackson.databind.introspect.BeanPropertyDefinition;
import tools.jackson.module.blackbird.codegen.BeanReaderGenerator;
import tools.jackson.module.blackbird.codegen.CodegenFallbacks;

/**
 * Wraps eligible stock bean deserializers in a placeholder that generates a
 * per-bean codec at resolve time (ClassFile API hidden class). Every gate that
 * fails leaves the stock deserializer in place, so behavior never changes for
 * beans the generator does not fully understand.
 */
public class BBDeserializerModifier extends ValueDeserializerModifier
{
    private static final long serialVersionUID = 1L;

    // Kept for the released BlackbirdModule(Function) contract; the codec
    // no longer needs it. Member access rides databind's own fixAccess (see
    // MemberHandles), so a user lookup is not required for acceleration.
    private final Function<Class<?>, MethodHandles.Lookup> _lookups;

    // The build method is only reachable from updateBuilder; the factory calls
    // updateBuilder and modifyDeserializer for the same bean back to back on
    // one thread, so a ThreadLocal hands it across. Not final: readObject
    // recreates it, since transient fields deserialize as null.
    private transient ThreadLocal<AnnotatedMethod> _pendingBuildMethod = new ThreadLocal<>();

    public BBDeserializerModifier(Function<Class<?>, MethodHandles.Lookup> lookups) {
        _lookups = lookups;
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        _pendingBuildMethod = new ThreadLocal<>();
    }

    @Override
    public BeanDeserializerBuilder updateBuilder(DeserializationConfig config,
            BeanDescription.Supplier beanDescRef, BeanDeserializerBuilder builder) {
        _pendingBuildMethod.set(builder.getBuildMethod());
        return builder;
    }

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
        AnnotatedMethod buildMethod = _pendingBuildMethod.get();
        _pendingBuildMethod.remove();
        boolean builderBased = deserializer.getClass() == BuilderBasedDeserializer.class
                && buildMethod != null;
        if (!builderBased && deserializer.getClass() != BeanDeserializer.class) {
            return deserializer;
        }
        if (Boolean.TRUE.equals(config.getDefaultMergeable())) {
            return deserializer;
        }
        BeanDescription beanDesc = beanDescRef.get();
        Class<?> beanClass = beanDesc.getBeanClass();
        // Non-static inner classes construct against an enclosing instance,
        // which the generated loop does not model. No other class- or
        // constructor-shape gate remains: construction goes through a handle
        // or the stock instantiator, and the factory checks creator shape at
        // resolve time (creator strictness needs no gate - the generated
        // record path enforces required, FAIL_ON_MISSING, and FAIL_ON_NULL
        // creator semantics per call). The static check runs first: for a
        // static member class redefined in a foreign classloader,
        // getEnclosingClass raises IncompatibleClassChangeError, and such
        // beans accelerate now.
        if (!Modifier.isStatic(beanClass.getModifiers())
                && beanClass.getEnclosingClass() != null) {
            return deserializer;
        }
        if (!builderBased && !beanClass.isRecord()
                && Modifier.isAbstract(beanClass.getModifiers())) {
            return deserializer;
        }
        // Any-setter values apply to a live instance, which record codecs do
        // not have during the loop (stock buffers them for creator types);
        // POJO and builder codecs feed the stock any-setter from the unknown
        // arm, so only records demote.
        if (beanClass.isRecord() && beanDesc.findAnySetterAccessor() != null) {
            return deserializer;
        }
        // Injected values apply right after construction (the codec calls the
        // base injection helper before its loop, like stock), but record
        // codecs have no instance until the end of the document, so records
        // with injectables demote.
        if (beanClass.isRecord()) {
            Map<Object, ?> injectables = beanDesc.findInjectables();
            if (injectables != null && !injectables.isEmpty()) {
                return deserializer;
            }
        }
        // Ignored and included property sets ride into the codec, whose
        // unknown arm consults them in the stock loop's exact order.
        JsonIgnoreProperties.Value ignorals =
                config.getDefaultPropertyIgnorals(beanClass, beanDesc.getClassInfo());
        JsonIncludeProperties.Value inclusions =
                config.getDefaultPropertyInclusions(beanClass, beanDesc.getClassInfo());
        boolean ignoreAllUnknown = ignorals != null && ignorals.getIgnoreUnknown();
        Set<String> ignorable = new HashSet<>();
        if (ignorals != null) {
            ignorable.addAll(ignorals.getIgnored());
        }
        ignorable.addAll(beanDesc.getIgnoredPropertyNames());
        Set<String> includable = (inclusions == null) ? null : inclusions.getIncluded();
        BeanReaderGenerator.Ignorals codecIgnorals =
                (!ignoreAllUnknown && ignorable.isEmpty() && includable == null)
                        ? BeanReaderGenerator.Ignorals.NONE
                        : new BeanReaderGenerator.Ignorals(ignoreAllUnknown,
                                ignorable.isEmpty() ? null : Set.copyOf(ignorable),
                                includable);
        // Effective case-insensitivity, the way the stock builder computes it:
        // the per-class format override wins, the mapper feature is baseline.
        Boolean formatCI = beanDescRef.findExpectedFormat(null)
                .getFeature(JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES);
        boolean caseInsensitive = (formatCI == null)
                ? config.isEnabled(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
                : formatCI.booleanValue();
        boolean declaresViews = config.getAnnotationIntrospector()
                .findViews(config, beanDesc.getClassInfo()) != null;
        Map<String, List<PropertyName>> aliases = null;
        for (BeanPropertyDefinition def : beanDesc.findProperties()) {
            if (def.findViews() != null) {
                declaresViews = true;
            }
            List<PropertyName> defAliases = def.findAliases();
            if (!defAliases.isEmpty()) {
                if (aliases == null) {
                    aliases = new HashMap<>();
                }
                aliases.put(def.getName(), defAliases);
            }
            if (def.getPrimaryMember() != null
                    && config.getAnnotationIntrospector()
                            .findUnwrappingNameTransformer(config, def.getPrimaryMember()) != null) {
                return deserializer;
            }
            if (def.getMetadata() != null && def.getMetadata().getMergeInfo() != null) {
                return deserializer;
            }
        }
        return new BBReaderPlaceholder((BeanDeserializerBase) deserializer,
                builderBased ? buildMethod : null, declaresViews, codecIgnorals, aliases,
                caseInsensitive);
    }
}
