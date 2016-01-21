package com.fasterxml.jackson.module.mrbean;

import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JavaType;

public class BeanUtil
{
    protected static boolean isConcrete(Member member)
    {
        int mod = member.getModifiers();
        return (mod & (Modifier.INTERFACE | Modifier.ABSTRACT)) == 0;
    }
    
    /**
     * Method that will find all sub-classes and implemented interfaces
     * of a given class or interface. Classes are listed in order of
     * precedence, starting with the immediate super-class, followed by
     * interfaces class directly declares to implemented, and then recursively
     * followed by parent of super-class and so forth.
     * Note that <code>Object.class</code> is not included in the list
     * regardless of whether <code>endBefore</code> argument is defined or not.
     *
     * @param endBefore Super-type to NOT include in results, if any; when
     *    encountered, will be ignored (and no super types are checked).
     */
    public static List<JavaType> findSuperTypes(JavaType type, Class<?> endBefore)
    {
        return findSuperTypes(type, endBefore, new ArrayList<JavaType>());
    }

    public static List<JavaType> findSuperTypes(JavaType type, Class<?> endBefore, List<JavaType> result)
    {
        _addSuperTypes(type, endBefore, result, false);
        return result;
    }
    
    private static void _addSuperTypes(JavaType type, Class<?> endBefore,
            List<JavaType> result, boolean addClassItself)
    {
        if ((type == null) || type.isJavaLangObject() || type.hasRawClass(endBefore)) {
            return;
        }
        if (addClassItself) {
            // 28-Nov-2015, tatu: Should we check for differently parameterized generic types?
            //   For now, assume it's not a significant problem
            if (result.contains(type)) { // already added, no need to check supers
                return;
            }
            result.add(type);
        }
        for (JavaType intCls : type.getInterfaces()) {
            _addSuperTypes(intCls, endBefore, result, true);
        }
        _addSuperTypes(type.getSuperClass(), endBefore, result, true);
    }
}
