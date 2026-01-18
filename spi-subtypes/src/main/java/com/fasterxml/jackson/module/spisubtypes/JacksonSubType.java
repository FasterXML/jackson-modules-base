package com.fasterxml.jackson.module.spisubtypes;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.fasterxml.jackson.annotation.JacksonAnnotation;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeName;

/**
 * Definition of a subtype, along with optional name(s). If no name is defined
 * (empty Strings are ignored), class of the type will be checked for {@link JsonTypeName}
 * annotation; and if that is also missing or empty, a default
 * name will be constructed by type id mechanism.
 * Default name is usually based on class name.
 * <p>
 * It's the same as {@link  JsonSubTypes.Type}.
 *
 * @since 2.21 / 3.1
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotation
public @interface JacksonSubType {
    /**
     * Logical type name used as the type identifier for the class, if defined; empty
     * String means "not defined". Used unless {@link #names} is defined as non-empty.
     *
     * @return subtype name
     */
    String value() default "";

    /**
     * (optional) Logical type names used as the type identifier for the class: used if
     * more than one type name should be associated with the same type.
     *
     * @return subtype name array
     */
    String[] names() default {};
}
