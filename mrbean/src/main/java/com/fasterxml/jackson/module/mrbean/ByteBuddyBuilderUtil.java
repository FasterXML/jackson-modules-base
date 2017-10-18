package com.fasterxml.jackson.module.mrbean;

import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.Argument;
import net.bytebuddy.implementation.bind.annotation.This;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Random;

import static net.bytebuddy.matcher.ElementMatchers.named;

final class ByteBuddyBuilderUtil {

    private ByteBuddyBuilderUtil() {}

    static DynamicType.Builder<?> createEqualsAndHashCode(DynamicType.Builder<?> builder) {
        return builder
                 .method(named("equals")).intercept(MethodDelegation.to(EqualsTarget.class))
                 .method(named("hashCode")).intercept(MethodDelegation.to(HashCodeTarget.class));
    }

    private static class FieldUtil {
        /**
         * Create a map with the field name as the map key and the field value as the map value
         */
        private static HashMap<String, Object> toMap(Object o, Field[] fields) throws IllegalAccessException {
            final HashMap<String, Object> result = new HashMap<String, Object>();
            for (Field field : fields) {
                field.setAccessible(true);
                result.put(field.getName(), field.get(o));
            }
            return result;
        }
    }

    /**
     * Used as ByteBuddy target for the 'equals' method
     * Uses the declared Fields of the two objects to dynamically
     * determine equality.
     *
     * Needs to be public since it will be accessed by the generated class
     */
    public static class EqualsTarget {

        public static boolean intercept(@This Object self, @Argument(0) Object other) {
            if (null == other) {
                return false;
            }
            //we are being strict about class equality
            final Class<?> selfClass = self.getClass();
            final Class<?> otherClass = other.getClass();
            if (selfClass != otherClass) {
                return false;
            }
            //take advantage of HashMap's built-in equals method
            try {
                return FieldUtil.toMap(self, selfClass.getDeclaredFields())
                        .equals(FieldUtil.toMap(other, other.getClass().getDeclaredFields()));
            } catch (IllegalAccessException e) {
                return false; //this it not a good solution, but should never happen because fields are accessible
            }
        }
    }

    /**
     * Used as ByteBuddy target for the 'hashCode' method
     * Uses the declared Fields of the object to dynamically
     * determine the hashCode value.
     *
     * Needs to be public since it will be accessed by the generated class
     */
    public static class HashCodeTarget {

        public static int intercept(@This Object self) {
            try {
                return FieldUtil.toMap(self, self.getClass().getDeclaredFields()).hashCode();
            } catch (IllegalAccessException e) {
                //this it not a good solution, but should never happen because fields are accessible
                return 31 + new Random().nextInt(10000);
            }
        }
    }
}
