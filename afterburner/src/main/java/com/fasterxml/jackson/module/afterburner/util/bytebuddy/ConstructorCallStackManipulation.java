package com.fasterxml.jackson.module.afterburner.util.bytebuddy;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.bytecode.Duplication;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.implementation.bytecode.TypeCreation;
import net.bytebuddy.jar.asm.MethodVisitor;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static net.bytebuddy.description.method.MethodDescription.ForLoadedConstructor;
import static net.bytebuddy.description.method.MethodDescription.InDefinedShape;
import static net.bytebuddy.implementation.bytecode.member.MethodInvocation.invoke;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * Base class for providing a constructor call Implementation
 */
public abstract class ConstructorCallStackManipulation implements StackManipulation {

    private final List<StackManipulation> constructorArgumentLoadingOperations;

    public ConstructorCallStackManipulation() {
        this(new ArrayList<StackManipulation>());
    }

    public ConstructorCallStackManipulation(StackManipulation... constructorArgumentLoadingOperations) {
        this(Arrays.asList(constructorArgumentLoadingOperations));
    }

    public ConstructorCallStackManipulation(List<StackManipulation> constructorArgumentLoadingOperations) {
        this.constructorArgumentLoadingOperations =
                null == constructorArgumentLoadingOperations ?
                        new ArrayList<StackManipulation>() :
                        constructorArgumentLoadingOperations;
    }

    public abstract TypeDescription determineTypeDescription(Implementation.Context implementationContext);
    public abstract InDefinedShape determineConstructor(TypeDescription typeDescription);

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public StackManipulation.Size apply(MethodVisitor methodVisitor, Implementation.Context implementationContext) {
        final TypeDescription typeDescription = determineTypeDescription(implementationContext);

        final List<StackManipulation> stackManipulations = new ArrayList<>();
        stackManipulations.add(TypeCreation.of(typeDescription)); //new
        stackManipulations.add(Duplication.of(typeDescription)); //dup
        stackManipulations.addAll(constructorArgumentLoadingOperations); //load any needed variables
        stackManipulations.add(invoke(determineConstructor(typeDescription))); //invokespecial

        final StackManipulation.Compound delegate = new StackManipulation.Compound(stackManipulations);
        return delegate.apply(methodVisitor, implementationContext);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ConstructorCallStackManipulation that = (ConstructorCallStackManipulation) o;

        return constructorArgumentLoadingOperations != null ? constructorArgumentLoadingOperations.equals(that.constructorArgumentLoadingOperations) : that.constructorArgumentLoadingOperations == null;
    }

    @Override
    public int hashCode() {
        return constructorArgumentLoadingOperations != null ? constructorArgumentLoadingOperations.hashCode() : 0;
    }

    /**
     * To be used when a reference to java.lang.reflect.Constructor needs to be invoked
     */
    public static class KnownConstructorOfExistingType extends ConstructorCallStackManipulation {

        private final Constructor<?> ctor;
        private final TypeDescription typeDescription;

        public KnownConstructorOfExistingType(Constructor<?> ctor) {
            this(ctor, new ArrayList<StackManipulation>());
        }

        public KnownConstructorOfExistingType(Constructor<?> ctor,
                                              StackManipulation... constructorArgumentLoadingOperations) {
            this(ctor, Arrays.asList(constructorArgumentLoadingOperations));
        }

        public KnownConstructorOfExistingType(Constructor<?> ctor,
                                              List<StackManipulation> constructorArgumentLoadingOperations) {
            super(constructorArgumentLoadingOperations);
            this.ctor = ctor;
            typeDescription = new TypeDescription.ForLoadedType(ctor.getDeclaringClass());
        }

        @Override
        public TypeDescription determineTypeDescription(Implementation.Context implementationContext) {
            return typeDescription;
        }

        @Override
        public InDefinedShape determineConstructor(TypeDescription td) {
            return new ForLoadedConstructor(ctor);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;

            KnownConstructorOfExistingType that = (KnownConstructorOfExistingType) o;

            return ctor.equals(that.ctor);
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + ctor.hashCode();
            return result;
        }
    }

    public static class KnownInDefinedShapeOfExistingType extends ConstructorCallStackManipulation {
        private final MethodDescription.InDefinedShape ctor;
        private final TypeDescription typeDescription;

        public KnownInDefinedShapeOfExistingType(InDefinedShape ctor) {
            this(ctor, new ArrayList<StackManipulation>());
        }

        public KnownInDefinedShapeOfExistingType(InDefinedShape ctor,
                                                 StackManipulation... constructorArgumentLoadingOperations) {
            this(ctor, Arrays.asList(constructorArgumentLoadingOperations));
        }

        public KnownInDefinedShapeOfExistingType(InDefinedShape ctor,
                                                 List<StackManipulation> constructorArgumentLoadingOperations) {
            super(constructorArgumentLoadingOperations);
            this.ctor = ctor;
            typeDescription = ctor.getDeclaringType();
        }

        @Override
        public TypeDescription determineTypeDescription(Implementation.Context implementationContext) {
            return typeDescription;
        }

        @Override
        public InDefinedShape determineConstructor(TypeDescription td) {
            return ctor;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;

            KnownInDefinedShapeOfExistingType that = (KnownInDefinedShapeOfExistingType) o;

            return ctor.equals(that.ctor);
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + ctor.hashCode();
            return result;
        }
    }

    /**
     * To be used when we need to call a constructor of the instrumented type
     */
    public abstract static class OfInstrumentedType extends ConstructorCallStackManipulation {

        public OfInstrumentedType() {
            super();
        }

        public OfInstrumentedType(StackManipulation... constructorArgumentLoadingOperations) {
            super(constructorArgumentLoadingOperations);
        }

        public OfInstrumentedType(List<StackManipulation> constructorArgumentLoadingOperations) {
            super(constructorArgumentLoadingOperations);
        }

        abstract InDefinedShape pickConstructor(
                MethodList<MethodDescription.InDefinedShape> candidates);

        @Override
        public TypeDescription determineTypeDescription(Implementation.Context implementationContext) {
            return implementationContext.getInstrumentedType();
        }

        @Override
        public InDefinedShape determineConstructor(TypeDescription typeDescription) {
            return pickConstructor(typeDescription.getDeclaredMethods().filter(isConstructor()));
        }

        /**
         * To be used when we need to call the only constructor of the instrumented type
         * that takes a single argument
         */
        public static class OneArg extends OfInstrumentedType {

            public OneArg() {
                super();
            }

            public OneArg(StackManipulation... constructorArgumentLoadingOperations) {
                super(constructorArgumentLoadingOperations);
            }

            public OneArg(List<StackManipulation> constructorArgumentLoadingOperations) {
                super(constructorArgumentLoadingOperations);
            }

            @Override
            InDefinedShape pickConstructor(MethodList<InDefinedShape> candidates) {
                return candidates.filter(takesArguments(1)).getOnly();
            }

        }
    }

}
