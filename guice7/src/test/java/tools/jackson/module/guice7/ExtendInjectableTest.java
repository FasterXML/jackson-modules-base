package tools.jackson.module.guice7;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.name.Names;

import com.fasterxml.jackson.annotation.*;

import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ExtendInjectableTest
{
    final ConstructorDependency constructorInjected = new ConstructorDependency();
    final ConstructorDependency constructorInjectedWithCustomAnnotation = new ConstructorDependency();
    final ConstructorDependency constructorInjectedWithGuiceAnnotation = new ConstructorDependency();
    final ConstructorDependency constructorInjectedWithJavaAnnotation = new ConstructorDependency();
    final FieldDependency fieldInjected = new FieldDependency();
    final FieldDependency fieldInjectedWithCustomAnnotation = new FieldDependency();
    final FieldDependency fieldInjectedWithGuiceAnnotation = new FieldDependency();
    final FieldDependency fieldInjectedWithJavaAnnotation = new FieldDependency();
    final MethodDependency methodInjected = new MethodDependency();
    final MethodDependency methodInjectedWithCustomAnnotation = new MethodDependency();
    final MethodDependency methodInjectedWithGuiceAnnotation = new MethodDependency();
    final MethodDependency methodInjectedWithJavaAnnotation = new MethodDependency();

    @Test
    public void testModulesRegisteredThroughInjectionWithKey() throws Exception
    {
        final Injector injector = Guice.createInjector(new ObjectMapperModule(),
                                                       new Module() {
                @Override
                public void configure(Binder binder)
                {
                    binder.bind(ConstructorDependency.class).toInstance(constructorInjected);
                    binder.bind(ConstructorDependency.class).annotatedWith(Ann.class).toInstance(constructorInjectedWithCustomAnnotation);
                    binder.bind(ConstructorDependency.class).annotatedWith(Names.named("guice")).toInstance(constructorInjectedWithGuiceAnnotation);
                    binder.bind(ConstructorDependency.class).annotatedWith(Names.named("jakarta")).toInstance(constructorInjectedWithJavaAnnotation);
                    binder.bind(FieldDependency.class).toInstance(fieldInjected);
                    binder.bind(FieldDependency.class).annotatedWith(Ann.class).toInstance(fieldInjectedWithCustomAnnotation);
                    binder.bind(FieldDependency.class).annotatedWith(Names.named("guice")).toInstance(fieldInjectedWithGuiceAnnotation);
                    binder.bind(FieldDependency.class).annotatedWith(Names.named("jakarta")).toInstance(fieldInjectedWithJavaAnnotation);
                    binder.bind(MethodDependency.class).toInstance(methodInjected);
                    binder.bind(MethodDependency.class).annotatedWith(Ann.class).toInstance(methodInjectedWithCustomAnnotation);
                    binder.bind(MethodDependency.class).annotatedWith(Names.named("guice")).toInstance(methodInjectedWithGuiceAnnotation);
                    binder.bind(MethodDependency.class).annotatedWith(Names.named("jakarta")).toInstance(methodInjectedWithJavaAnnotation);
                }
        });

        /* First of all, just get an InjectableBean out of guice to make sure it's correct (test the test) */
        verifyInjections("From Guice's Injector", injector.getInstance(InjectableBean.class));

        /* Now let's try injections via our ObjectMapper (plus some values) */
        final ObjectMapper mapper = injector.getInstance(ObjectMapper.class);

        final ReadableBean bean = mapper.readValue("{\"constructor_value\":\"myConstructor\",\"field_value\":\"myField\",\"method_value\":\"myMethod\"}", ReadableBean.class);

        assertEquals("myConstructor", bean.constructorValue);
        assertEquals("myMethod",      bean.methodValue);
        assertEquals("myField",       bean.fieldValue);

        verifyInjections("From Jackson's ObjectMapper", bean);

    }

    private void verifyInjections(String message, InjectableBean injected)
    {
        assertSame(constructorInjected,                     injected.constructorInjected);
        assertSame(constructorInjectedWithJavaAnnotation,   injected.constructorInjectedWithJavaAnnotation);
        assertSame(constructorInjectedWithGuiceAnnotation,  injected.constructorInjectedWithGuiceAnnotation);
        assertSame(constructorInjectedWithCustomAnnotation, injected.constructorInjectedWithCustomAnnotation);

        assertSame(methodInjected,                     injected.methodInjected);
        assertSame(methodInjectedWithJavaAnnotation,   injected.methodInjectedWithJavaAnnotation);
        assertSame(methodInjectedWithGuiceAnnotation,  injected.methodInjectedWithGuiceAnnotation);
        assertSame(methodInjectedWithCustomAnnotation, injected.methodInjectedWithCustomAnnotation);

        assertSame(fieldInjected,                     injected.fieldInjected);
        assertSame(fieldInjectedWithJavaAnnotation,   injected.fieldInjectedWithJavaAnnotation);
        assertSame(fieldInjectedWithGuiceAnnotation,  injected.fieldInjectedWithGuiceAnnotation);
        assertSame(fieldInjectedWithCustomAnnotation, injected.fieldInjectedWithCustomAnnotation);
    }

    /* ===================================================================== */
    /* SUPPORT CLASSES                                                       */
    /* ===================================================================== */

    static class ReadableBean extends InjectableBean {

        @JsonProperty("field_value")
        String fieldValue;
        String methodValue;
        final String constructorValue;

        @JsonCreator
        private ReadableBean(@JacksonInject ConstructorDependency constructorInjected,
                             @JacksonInject @jakarta.inject.Named("jakarta") ConstructorDependency constructorInjectedWithJavaAnnotation,
                             @JacksonInject @com.google.inject.name.Named("guice") ConstructorDependency constructorInjectedWithGuiceAnnotation,
                             @JacksonInject @Ann ConstructorDependency constructorInjectedWithCustomAnnotation,
                             @JsonProperty("constructor_value") String constructorValue)
        {
            super(constructorInjected,
                  constructorInjectedWithJavaAnnotation,
                  constructorInjectedWithGuiceAnnotation,
                  constructorInjectedWithCustomAnnotation);
            this.constructorValue = constructorValue;
        }

        @JsonProperty("method_value")
        public void setMethodValue(String methodValue)
        {
            this.methodValue = methodValue;
        }

    }

    /* ===================================================================== */

    static class ConstructorDependency {};
    static class MethodDependency {};
    static class FieldDependency {};

    static class InjectableBean
    {
        @Inject
        FieldDependency fieldInjected;
        MethodDependency methodInjected;
        final ConstructorDependency constructorInjected;

        @JacksonInject
        @Inject
        @jakarta.inject.Named("jakarta")
        FieldDependency fieldInjectedWithJavaAnnotation;
        MethodDependency methodInjectedWithJavaAnnotation;
        final ConstructorDependency constructorInjectedWithJavaAnnotation;

        @JacksonInject
        @Inject
        @com.google.inject.name.Named("guice")
        FieldDependency fieldInjectedWithGuiceAnnotation;
        MethodDependency methodInjectedWithGuiceAnnotation;
        final ConstructorDependency constructorInjectedWithGuiceAnnotation;

        @JacksonInject
        @Inject
        @Ann
        FieldDependency fieldInjectedWithCustomAnnotation;
        MethodDependency methodInjectedWithCustomAnnotation;
        final ConstructorDependency constructorInjectedWithCustomAnnotation;

        @Inject // this is simply to make sure we *can* build this correctly
        protected InjectableBean(ConstructorDependency constructorInjected,
                                 @jakarta.inject.Named("jakarta")  ConstructorDependency constructorInjectedWithJavaAnnotation,
                                 @com.google.inject.name.Named("guice") ConstructorDependency constructorInjectedWithGuiceAnnotation,
                                 @Ann ConstructorDependency constructorInjectedWithCustomAnnotation)
        {
            this.constructorInjected = constructorInjected;
            this.constructorInjectedWithJavaAnnotation = constructorInjectedWithJavaAnnotation;
            this.constructorInjectedWithGuiceAnnotation = constructorInjectedWithGuiceAnnotation;
            this.constructorInjectedWithCustomAnnotation = constructorInjectedWithCustomAnnotation;
        }

        @JacksonInject
        @Inject // not annotated
        private void inject1(MethodDependency dependency)
        {
            this.methodInjected = dependency;
        }

        @JacksonInject
        @Inject
        private void inject2(@jakarta.inject.Named("jakarta") MethodDependency dependency)
        {
            this.methodInjectedWithJavaAnnotation = dependency;
        }

        @JacksonInject
        @Inject
        private void inject3(@com.google.inject.name.Named("guice") MethodDependency dependency)
        {
            this.methodInjectedWithGuiceAnnotation = dependency;
        }

        @JacksonInject
        @Inject
        private void inject4(@Ann MethodDependency dependency)
        {
            this.methodInjectedWithCustomAnnotation = dependency;
        }

    }
}
