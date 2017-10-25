package com.fasterxml.jackson.module.guice;

import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.databind.introspect.*;
import com.google.inject.BindingAnnotation;
import com.google.inject.Key;

import javax.inject.Qualifier;
import java.lang.annotation.Annotation;
import java.util.Arrays;

public class GuiceAnnotationIntrospector extends NopAnnotationIntrospector
{
    private static final long serialVersionUID = 1L;

    @Override // since 2.9
    public JacksonInject.Value findInjectableValue(AnnotatedMember m) {
        Object id = _findGuiceInjectId(m);
        if (id == null) {
            return null;
        }
        return JacksonInject.Value.forId(id);
    }

    private Object _findGuiceInjectId(AnnotatedMember m)
    {
        /*
        * We check on three kinds of annotations: @JacksonInject for types
        * that were actually created for Jackson, and @Inject (both Guice's
        * and javax.inject) for types that (for example) extend already
        * annotated objects.
        *
        * Postel's law: http://en.wikipedia.org/wiki/Robustness_principle
        */
        // 19-Apr-2017, tatu: Actually this is something that should not be done;
        //   instead, pair of AnnotationIntrospector should be used... Leaving in
        //   for now, however.
        if ((m.getAnnotation(JacksonInject.class) == null) &&
            (m.getAnnotation(javax.inject.Inject.class) == null) &&
            (m.getAnnotation(com.google.inject.Inject.class) == null))
        {
            return null;
        }

        final AnnotatedMember guiceMember;
        final Annotation guiceAnnotation;

        if ((m instanceof AnnotatedField) || (m instanceof AnnotatedParameter)) {
            // On fields and parameters the @Qualifier annotation and type to
            // inject are the member itself, so, nothing to do here...
            guiceMember = m;
            AnnotationMap anns = ((AnnotatedMember) m).getAllAnnotations();
            guiceAnnotation = findBindingAnnotation(anns.annotations());
        } else if (m instanceof AnnotatedMethod) {
           /* For method injection, the @Qualifier and type to inject are
            * specified on the parameter. Here, we only consider methods with
            * a single parameter.
            */
           final AnnotatedMethod a = (AnnotatedMethod) m;
           if (a.getParameterCount() != 1) {
               return null;
           }

           /* Jackson does not *YET* give us parameter annotations on methods,
            * only on constructors, henceforth we have to do a bit of work
            * ourselves!
            */
            guiceMember = a.getParameter(0);
            final Annotation[] annotations = a.getMember().getParameterAnnotations()[0];
            guiceAnnotation = findBindingAnnotation(Arrays.asList(annotations));
        } else {
            /* Ignore constructors */
            return null;
        }

        /* Depending on whether we have an annotation (or not) return the
         * correct Guice key that Jackson will use to query the Injector.
         */
        if (guiceAnnotation == null) {
            // 19-Sep-2016, tatu: Used to pass `getGenericType()`, but that is now deprecated.
            //    Looking at code in Guice Key, I don't think it does particularly good job
            //    in resolving generic types, so this is probably safe...
//            return Key.get(guiceMember.getGenericType());
            return Key.get((java.lang.reflect.Type) guiceMember.getRawType());
        }
//        return Key.get(guiceMember.getGenericType(), guiceAnnotation);
        return Key.get((java.lang.reflect.Type) guiceMember.getRawType(), guiceAnnotation);
    }

    /*
     * We want to figure out if a @BindingAnnotation or @Qualifier
     * annotation are present on what we're trying to inject.
     * Those annotations are only possible on fields or parameters.
     */
    private Annotation findBindingAnnotation(Iterable<Annotation> annotations)
    {
        for (Annotation annotation : annotations) {
            // Check on guice (BindingAnnotation) & javax (Qualifier) based injections
            if (annotation.annotationType().isAnnotationPresent(BindingAnnotation.class) ||
                annotation.annotationType().isAnnotationPresent(Qualifier.class))
            {
                return annotation;
            }
        }
        return null;
    }
}
