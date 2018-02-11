 package com.fasterxml.jackson.module.afterburner.ser;

import java.lang.reflect.Method;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.util.SimpleBeanPropertyDefinition;
import com.fasterxml.jackson.module.afterburner.AfterburnerTestBase;

public class TestAccessorGeneration extends AfterburnerTestBase
{
    /*
    /**********************************************************************
    /* Helper types
    /**********************************************************************
     */

    public static class Bean1 {
        public int getX() { return 13; }
    }

    public static class Bean3 {
        public int getX() { return 13; }
        public int getY() { return 27; }
        public int get3() { return 3; }
    }

    public static class BeanN {
        public int getX() { return 13; }
        public int getY() { return 27; }

        public int get3() { return 3; }
        public int get4() { return 4; }
        public int get5() { return 5; }
        public int get6() { return 6; }
        public int get7() { return 7; }
    }
    
    /*
    /**********************************************************************
    /* Test methods
    /**********************************************************************
     */

    // We need MapperConfig to pass, so easiest way is to:
    private final ObjectMapper MAPPER = new ObjectMapper();
    private final MapperConfig<?> MAPPER_CONFIG = MAPPER.serializationConfig();

    public void testSingleIntAccessorGeneration() throws Exception
    {
        Method method = Bean1.class.getDeclaredMethod("getX");
        AnnotatedMethod annMethod = new AnnotatedMethod(null, method, null, null);
        PropertyAccessorCollector coll = new PropertyAccessorCollector(Bean1.class);
        BeanPropertyWriter bpw = new BeanPropertyWriter(SimpleBeanPropertyDefinition
                .construct(MAPPER_CONFIG, annMethod, new PropertyName("x")),
                annMethod, null,
                null,
                null, null, null,
                false, null, null);
        coll.addIntGetter(bpw);
        BeanPropertyAccessor acc = coll.findAccessor(null);
        Bean1 bean = new Bean1();
        int value = acc.intGetter(bean, 0);
        assertEquals(bean.getX(), value);
    }

    public void testDualIntAccessorGeneration() throws Exception
    {
        PropertyAccessorCollector coll = new PropertyAccessorCollector(Bean3.class);

        String[] methodNames = new String[] {
                "getX", "getY", "get3"
        };
        
        /*
    public BeanPropertyWriter(BeanPropertyDefinition propDef,
            AnnotatedMember member, Annotations contextAnnotations,
            JavaType declaredType,
            JsonSerializer<Object> ser, TypeSerializer typeSer, JavaType serType,
            boolean suppressNulls, Object suppressableValue)
         */
        
        for (String methodName : methodNames) {
            Method method = Bean3.class.getDeclaredMethod(methodName);
            AnnotatedMethod annMethod = new AnnotatedMethod(null, method, null, null);
            // should we translate from method name to property name?
            coll.addIntGetter(new BeanPropertyWriter(SimpleBeanPropertyDefinition
                    .construct(MAPPER_CONFIG, annMethod, new PropertyName(methodName)),
                    annMethod, null,
                    null,
                    null, null, null,
                    false, null, null));
        }

        BeanPropertyAccessor acc = coll.findAccessor(null);
        Bean3 bean = new Bean3();

        assertEquals(bean.getX(), acc.intGetter(bean, 0));
        assertEquals(bean.getY(), acc.intGetter(bean, 1));
        assertEquals(bean.get3(), acc.intGetter(bean, 2));
    }

    // And then test to ensure Switch-table construction also works...
    public void testLotsaIntAccessorGeneration() throws Exception
    {
        PropertyAccessorCollector coll = new PropertyAccessorCollector(BeanN.class);
        String[] methodNames = new String[] {
                "getX", "getY", "get3", "get4", "get5", "get6", "get7"
        };
        for (String methodName : methodNames) {
            Method method = BeanN.class.getDeclaredMethod(methodName);
            AnnotatedMethod annMethod = new AnnotatedMethod(null, method, null, null);
            coll.addIntGetter(new BeanPropertyWriter(SimpleBeanPropertyDefinition.construct(
                    MAPPER_CONFIG, annMethod, new PropertyName(methodName)),
                    annMethod, null,
                    null,
                    null, null, null,
                    false, null, null));
        }

        BeanPropertyAccessor acc = coll.findAccessor(null);
        BeanN bean = new BeanN();

        assertEquals(bean.getX(), acc.intGetter(bean, 0));
        assertEquals(bean.getY(), acc.intGetter(bean, 1));

        assertEquals(bean.get3(), acc.intGetter(bean, 2));
        assertEquals(bean.get4(), acc.intGetter(bean, 3));
        assertEquals(bean.get5(), acc.intGetter(bean, 4));
        assertEquals(bean.get6(), acc.intGetter(bean, 5));
        assertEquals(bean.get7(), acc.intGetter(bean, 6));
    }
}
