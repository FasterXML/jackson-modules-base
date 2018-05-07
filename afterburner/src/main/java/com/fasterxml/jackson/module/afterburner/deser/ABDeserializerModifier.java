package com.fasterxml.jackson.module.afterburner.deser;

import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.util.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.*;
import com.fasterxml.jackson.databind.deser.impl.FieldProperty;
import com.fasterxml.jackson.databind.deser.impl.MethodProperty;
import com.fasterxml.jackson.databind.deser.std.StdValueInstantiator;
import com.fasterxml.jackson.databind.introspect.*;
import com.fasterxml.jackson.databind.util.ClassUtil;
import com.fasterxml.jackson.module.afterburner.util.MyClassLoader;

public class ABDeserializerModifier extends BeanDeserializerModifier
{
    /**
     * Class loader to use for generated classes; if null, will try to
     * use class loader of the target class.
     */
    protected final MyClassLoader _classLoader;
    
    protected final boolean _useCustomDeserializer;

    public ABDeserializerModifier(ClassLoader cl, boolean useCustomDeserializer)
    {
        // If we were given parent class loader explicitly, use that:
        _classLoader = (cl == null) ? null : new MyClassLoader(cl, false);
        _useCustomDeserializer = useCustomDeserializer;
    }

    /*
    /********************************************************************** 
    /* BeanDeserializerModifier methods
    /********************************************************************** 
     */
   
    @Override
    public BeanDeserializerBuilder updateBuilder(DeserializationConfig config,
            BeanDescription beanDesc, BeanDeserializerBuilder builder) 
    {
        final Class<?> beanClass = beanDesc.getBeanClass();
        // [module-afterburner#21]: Can't force access to sealed packages, or anything within "java."
        if (!MyClassLoader.canAddClassInPackageOf(beanClass)) {
            return builder;
        } 
        /* Hmmh. Can we access stuff from private classes?
         * Possibly, if we can use parent class loader.
         * (should probably skip all non-public?)
         */
        if (_classLoader != null) {
            if (Modifier.isPrivate(beanClass.getModifiers())) {
                return builder;
            }
        }
        PropertyMutatorCollector collector = new PropertyMutatorCollector(beanClass);
        List<OptimizedSettableBeanProperty<?>> newProps = findOptimizableProperties(
                config, collector, builder.getProperties());
        // and if we found any, create mutator proxy, replace property objects
        if (!newProps.isEmpty()) {
            BeanPropertyMutator baseMutator = collector.buildMutator(_classLoader);
            for (OptimizedSettableBeanProperty<?> prop : newProps) {
                builder.addOrReplaceProperty(prop.withMutator(baseMutator), true);
            }
        }
        // Second thing: see if we could (re)generate Creator(s):
        ValueInstantiator inst = builder.getValueInstantiator();
        /* Hmmh. Probably better to require exact default implementation
         * and not sub-class; chances are sub-class uses its own
         * construction anyway.
         */
        if (inst.getClass() == StdValueInstantiator.class) {
            // also, only override if using default creator (no-arg ctor, no-arg static factory)
            if (inst.canCreateUsingDefault()) {
                inst = new CreatorOptimizer(beanClass, _classLoader, (StdValueInstantiator) inst).createOptimized();
                if (inst != null) {
                    builder.setValueInstantiator(inst);
                }
            }
        }

        // also: may want to replace actual BeanDeserializer as well? For this, need to replace builder
        // (but only if builder is the original standard one; don't want to break other impls)
        if (_useCustomDeserializer && builder.getClass() == BeanDeserializerBuilder.class) {
            return new SuperSonicDeserializerBuilder(builder);
        }
        return builder;
    }

    /*
    /********************************************************************** 
    /* Internal methods
    /********************************************************************** 
     */
    
    protected List<OptimizedSettableBeanProperty<?>> findOptimizableProperties(
            DeserializationConfig config, PropertyMutatorCollector collector,
            Iterator<SettableBeanProperty> propIterator)
    {
        ArrayList<OptimizedSettableBeanProperty<?>> newProps = new ArrayList<OptimizedSettableBeanProperty<?>>();

        // Ok, then, find any properties for which we could generate accessors
        while (propIterator.hasNext()) {
            SettableBeanProperty prop = propIterator.next();
            AnnotatedMember member = prop.getMember();
            Member jdkMember = member.getMember();

            // if we ever support virtual properties, this would be null, so check, skip
            if (jdkMember == null) {
                continue;
            }
            // First: we can't access private fields or methods....
            if (Modifier.isPrivate(jdkMember.getModifiers())) {
                continue;
            }
            // (although, interestingly enough, can seem to access private classes...)
            
            // 30-Jul-2012, tatu: [module-afterburner#6]: Needs to skip custom deserializers, if any.
            if (prop.hasValueDeserializer()) {
                if (!isDefaultDeserializer(prop.getValueDeserializer())) {
                    continue;
                }
            }

            if (prop instanceof MethodProperty) { // simple setter methods
                Class<?> type = ((AnnotatedMethod) member).getRawParameterType(0);
                if (type.isPrimitive()) {
                    if (type == Integer.TYPE) {
                        newProps.add(collector.addIntSetter(prop));
                    } else if (type == Long.TYPE) {
                        newProps.add(collector.addLongSetter(prop));
                    } else if (type == Boolean.TYPE) {
                        newProps.add(collector.addBooleanSetter(prop));
                    }
                } else {
                    if (type == String.class) {
                        newProps.add(collector.addStringSetter(prop));
                    } else { // any other Object types; we can at least call accessor
                        newProps.add(collector.addObjectSetter(prop));
                    }
                }
            } else if (prop instanceof FieldProperty) { // regular fields
                // And as to fields, can not overwrite final fields (which may
                // be overwritable via Reflection)
                if (Modifier.isFinal(prop.getMember().getMember().getModifiers())) {
                    continue;
                }
                
                Class<?> type = member.getRawType();
                if (type.isPrimitive()) {
                    if (type == Integer.TYPE) {
                        newProps.add(collector.addIntField(prop));
                    } else if (type == Long.TYPE) {
                        newProps.add(collector.addLongField(prop));
                    } else if (type == Boolean.TYPE) {
                        newProps.add(collector.addBooleanField(prop));
                    }
                } else {
                    if (type == String.class) {
                        newProps.add(collector.addStringField(prop));
                    } else { // any other Object types; we can at least call accessor
                        newProps.add(collector.addObjectField(prop));
                    }
                } 
            }
        }
        return newProps;
    }

    /**
     * Helper method used to check whether given deserializer is the default
     * deserializer implementation: this is necessary to avoid overriding other
     * kinds of deserializers.
     */
    protected boolean isDefaultDeserializer(JsonDeserializer<?> deser) {
        return ClassUtil.isJacksonStdImpl(deser)
                // 07-May-2018, tatu: Probably can't happen but just in case
                || (deser instanceof SuperSonicBeanDeserializer);

    }
}
