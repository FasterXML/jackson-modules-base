package com.fasterxml.jackson.module.mrbean;

import com.fasterxml.jackson.databind.JavaType;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeDescription.Generic;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;


final class TypeDefinitionUtil {

    private TypeDefinitionUtil() {}

    static TypeDefinition createTypeDefinitionFromJavaType(JavaType javaType) {
        if (!javaType.hasGenericTypes()) {
            //simply use the raw class to construct the corresponding TypeDefinition
            return new TypeDescription.ForLoadedType(javaType.getRawClass());
        }
        //create the appropriate Generic TypeDescription using containedType values
        final List<Type> genericParameters = new ArrayList<Type>();
        for(int i=0; i<javaType.containedTypeCount(); i++) {
            genericParameters.add(javaType.containedType(i).getRawClass());
        }
        return Generic.Builder.parameterizedType(javaType.getRawClass(), genericParameters).build();
    }
}
