package com.fasterxml.jackson.module.afterburner.ser;

import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.util.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import com.fasterxml.jackson.databind.ser.*;
import com.fasterxml.jackson.databind.util.ClassUtil;
import com.fasterxml.jackson.module.afterburner.util.MyClassLoader;

public class ABSerializerModifier extends BeanSerializerModifier
{
    /**
     * Class loader to use for generated classes; if null, will try to
     * use class loader of the target class.
     */
    protected final MyClassLoader _classLoader;
    
    public ABSerializerModifier(ClassLoader cl)
    {
        // If we were given parent class loader explicitly, use that:
        _classLoader = (cl == null) ? null : new MyClassLoader(cl, false);
    }

    @Override
    public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
            BeanDescription beanDesc, List<BeanPropertyWriter> beanProperties)
    {
        final Class<?> beanClass = beanDesc.getBeanClass();
        // [Issue#21]: Can't force access to sealed packages, or anything within "java."
        //    namespace. (how about javax.?)
        if (!MyClassLoader.canAddClassInPackageOf(beanClass)) {
            return beanProperties;
        }

        /* Hmmh. Can we access stuff from private classes?
         * Possibly, if we can use parent class loader.
         * (should probably skip all non-public?)
         */
        if (_classLoader != null) {
            if (Modifier.isPrivate(beanClass.getModifiers())) {
                return beanProperties;
            }
        }
        
        PropertyAccessorCollector collector = findProperties(beanClass, config, beanProperties);
        if (collector.isEmpty()) {
            return beanProperties;
        }
        
        // if we had a match, need to materialize
        BeanPropertyAccessor acc = null;

        // and then link accessors to bean property writers:
        ListIterator<BeanPropertyWriter> it = beanProperties.listIterator();
        while (it.hasNext()) {
            BeanPropertyWriter bpw = it.next();
            if (bpw instanceof OptimizedBeanPropertyWriter<?>) {
                if (acc == null) {
                    acc = collector.findAccessor(_classLoader);
                }
                it.set(((OptimizedBeanPropertyWriter<?>) bpw).withAccessor(acc));
            }
        }
        return beanProperties;
    }

    protected PropertyAccessorCollector findProperties(Class<?> beanClass,
            SerializationConfig config, List<BeanPropertyWriter> beanProperties)
    {
        PropertyAccessorCollector collector = new PropertyAccessorCollector(beanClass);
        ListIterator<BeanPropertyWriter> it = beanProperties.listIterator();
        while (it.hasNext()) {
            BeanPropertyWriter bpw = it.next();
            AnnotatedMember member = bpw.getMember();

            Member jdkMember = member.getMember();
            // 11-Sep-2015, tatu: Let's skip virtual members (related to #57)
            if (jdkMember == null) {
                continue;
            }
            // We can't access private fields or methods, skip:
            if (Modifier.isPrivate(jdkMember.getModifiers())) {
                continue;
            }
            // (although, interestingly enough, can seem to access private classes...)
            
            // 30-Jul-2012, tatu: [#6]: Needs to skip custom serializers, if any.
            if (bpw.hasSerializer()) {
                if (!isDefaultSerializer(config, bpw.getSerializer())) {
                    continue;
                }
            }
            // [#9]: also skip unwrapping stuff...
            if (bpw.isUnwrapping()) {
                continue;
            }
            // [#51]: and any sub-classes as well
            /* 04-Mar-2015, tatu: This might be too restrictive, as core databind has some 
             *   other sub-classes; if this becomes problematic may start using annotation
             *   to indicate "standard" implementations. But for now this solves the issue.
             */
            if (bpw.getClass() != BeanPropertyWriter.class) {
                continue;
            }

            // 11-Apr-2016, tatu: Actually we have to consider actual physical type
            //   of accessor, not just logical type, see [afterburner#4]; generic
            //   types
//            Class<?> type = bpw.getType().getRawClass();
            Class<?> type = bpw.getMember().getRawType();
            boolean isMethod = (member instanceof AnnotatedMethod);
            
            if (type.isPrimitive()) {
                if (type == Integer.TYPE) {

                    if (isMethod) {
                        it.set(collector.addIntGetter(bpw));
                    } else {
                        it.set(collector.addIntField(bpw));
                    }
                } else if (type == Long.TYPE) {
                    if (isMethod) {
                        it.set(collector.addLongGetter(bpw));
                    } else {
                        it.set(collector.addLongField(bpw));
                    }
                } else if (type == Boolean.TYPE) {
                    if (isMethod) {
                        it.set(collector.addBooleanGetter(bpw));
                    } else {
                        it.set(collector.addBooleanField(bpw));
                    }
                }
            } else {
                if (type == String.class) {
                    if (isMethod) {
                        it.set(collector.addStringGetter(bpw));
                    } else {
                        it.set(collector.addStringField(bpw));
                    }
                } else { // any other Object types; we can at least call accessor
                    if (isMethod) {
                        it.set(collector.addObjectGetter(bpw));
                    } else {
                        it.set(collector.addObjectField(bpw));
                    }
                }
            }
        }
        return collector;
    }

    // 25-Oct-2017, tatu: Originally prototyped here, but unrolling implementation
    //    merged in core databind for 3.0, so...
    /*
    @Override
    public JsonSerializer<?> modifySerializer(SerializationConfig config,
            BeanDescription beanDesc, JsonSerializer<?> serializer)
    {
        // 20-Oct-2017, tatu: Could add support for `BeanAsArraySerializer` and
        //    `UnwrappingBeanSerializer` too, but for now focus on "vanilla" case
        if (serializer.getClass() == BeanSerializer.class) {
            BeanSerializer base = (BeanSerializer) serializer;
            if (base.propertyCount() <= 6) {
                return new SuperSonicBeanSerializer(base);
            }
        }
        return serializer;
    }
    */

    /**
     * Helper method used to check whether given serializer is the default
     * serializer implementation: this is necessary to avoid overriding other
     * kinds of serializers.
     */
    protected boolean isDefaultSerializer(SerializationConfig config,
            JsonSerializer<?> ser)
    {
        return ClassUtil.isJacksonStdImpl(ser);
    }
}
