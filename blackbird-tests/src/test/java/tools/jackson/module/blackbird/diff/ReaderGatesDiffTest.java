package tools.jackson.module.blackbird.diff;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreType;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.deser.BeanDeserializerBuilder;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.module.SimpleModule;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reader features that the generated loop must leave to stock: polymorphic
 * property values, non-standard creation (external type ids, delegating
 * creators), property wrappers, and the ignoral sets stock computes. Each
 * scenario has a plain control bean that must keep the generated codec.
 */
public class ReaderGatesDiffTest extends DifferentialTestBase
{
    public static class Plain {
        public String name;
        public int count;
    }

    /*
    /**********************************************************************
    /* Polymorphic property values
    /**********************************************************************
     */

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@t")
    @JsonSubTypes({ @JsonSubTypes.Type(value = Dog.class, name = "dog") })
    public static class Animal {
        public String name;
    }

    public static class Dog extends Animal {
        public boolean bark;
    }

    public static class Owner {
        public String name;
        public Animal pet;
    }

    @Test
    public void polymorphicPropertyKeepsSubtype() {
        Mappers m = mappers();
        Owner owner = assertSameRead(m,
                "{\"name\":\"o\",\"pet\":{\"@t\":\"dog\",\"name\":\"rex\",\"bark\":true}}",
                Owner.class).get();
        assertEquals(Dog.class, owner.pet.getClass());
        assertTrue(((Dog) owner.pet).bark);
        String layout = assertGeneratedReader(m.blackbird(), Owner.class);
        assertArm(layout, "pet=STOCK");
        assertArm(layout, "name=STRING");
    }

    public static class Pet {
        public String name;
    }

    public static class Cat extends Pet {
        public int lives;
    }

    public static class PetHolder {
        @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
        @JsonSubTypes({ @JsonSubTypes.Type(value = Cat.class, name = "cat") })
        public Pet pet;
        public String tag;
    }

    @Test
    public void propertyLevelTypeInfoKeepsSubtype() {
        Mappers m = mappers();
        PetHolder h = assertSameRead(m,
                "{\"pet\":{\"@type\":\"cat\",\"name\":\"c\",\"lives\":9},\"tag\":\"t\"}",
                PetHolder.class).get();
        assertEquals(Cat.class, h.pet.getClass());
        String layout = assertGeneratedReader(m.blackbird(), PetHolder.class);
        assertArm(layout, "pet=STOCK");
        assertArm(layout, "tag=STRING");
    }

    public static class DefaultTyped {
        public Pet pet;
        public String tag;
    }

    @Test
    public void defaultTypingKeepsSubtype() {
        // As a property, the type id sits inside the value object.
        Mappers m = mappers(b -> b.activateDefaultTypingAsProperty(
                BasicPolymorphicTypeValidator.builder().allowIfBaseType(Object.class).build(),
                DefaultTyping.NON_FINAL, "@class"));
        DefaultTyped value = new DefaultTyped();
        Cat cat = new Cat();
        cat.name = "c";
        cat.lives = 7;
        value.pet = cat;
        value.tag = "t";
        String json = m.stock().writeValueAsString(value);
        DefaultTyped read = assertSameRead(m, json, DefaultTyped.class).get();
        assertEquals(Cat.class, read.pet.getClass());
        // Default typing types the property; the bean itself is non-final,
        // so it is read through the typed path, which stays stock.
        assertArm(assertGeneratedReader(m.blackbird(), DefaultTyped.class), "pet=STOCK");
    }

    /*
    /**********************************************************************
    /* Non-standard creation
    /**********************************************************************
     */

    public static class ExtBase {
        public String v;
    }

    public static class ExtImpl extends ExtBase {
        public int n;
    }

    public static class ExtHolder {
        public String note;
        @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
                property = "kind")
        @JsonSubTypes({ @JsonSubTypes.Type(value = ExtImpl.class, name = "impl") })
        public ExtBase payload;
    }

    @Test
    public void externalTypeIdKeepsStockBean() {
        Mappers m = mappers();
        ExtHolder h = assertSameRead(m,
                "{\"note\":\"x\",\"kind\":\"impl\",\"payload\":{\"v\":\"a\",\"n\":3}}",
                ExtHolder.class).get();
        assertEquals(ExtImpl.class, h.payload.getClass());
        assertEquals(3, ((ExtImpl) h.payload).n);
        assertStockReader(m.blackbird(), ExtHolder.class);
        assertGeneratedReader(m.blackbird(), Plain.class);
    }

    public static class Delegating {
        public String v;

        public Delegating() { }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static Delegating of(Map<String, Object> m) {
            Delegating d = new Delegating();
            d.v = "delegated:" + m.get("v");
            return d;
        }
    }

    @Test
    public void delegatingCreatorBesideDefaultConstructorKeepsStockBean() {
        Mappers m = mappers();
        Delegating d = assertSameRead(m, "{\"v\":\"x\",\"w\":1}", Delegating.class).get();
        assertEquals("delegated:x", d.v);
        assertStockReader(m.blackbird(), Delegating.class);
        assertGeneratedReader(m.blackbird(), Plain.class);
    }

    /*
    /**********************************************************************
    /* Property wrappers
    /**********************************************************************
     */

    public static class Parent {
        public String name;
        @JsonManagedReference
        public Kid kid;
    }

    public static class Kid {
        public String name;
        @JsonBackReference
        public Parent parent;
    }

    @Test
    public void managedReferenceSetsBackReference() {
        Mappers m = mappers();
        Parent p = assertSameRead(m, "{\"name\":\"p\",\"kid\":{\"name\":\"k\"}}",
                Parent.class).get();
        assertSame(p, p.kid.parent);
        Parent stock = m.stock().readValue("{\"name\":\"p\",\"kid\":{\"name\":\"k\"}}",
                Parent.class);
        assertSame(stock, stock.kid.parent);
        String layout = assertGeneratedReader(m.blackbird(), Parent.class);
        assertArm(layout, "kid=STOCK");
        assertArm(layout, "name=STRING");
    }

    // A user wrapper: upper-cases every String property value.
    static final class UpperCaseProperty extends SettableBeanProperty.Delegating {
        private static final long serialVersionUID = 1L;

        UpperCaseProperty(SettableBeanProperty d) {
            super(d);
        }

        @Override
        protected SettableBeanProperty withDelegate(SettableBeanProperty d) {
            return new UpperCaseProperty(d);
        }

        @Override
        public void deserializeAndSet(JsonParser p, DeserializationContext ctxt,
                Object instance) {
            delegate.set(ctxt, instance, p.getString().toUpperCase());
        }

        @Override
        public Object deserializeSetAndReturn(JsonParser p, DeserializationContext ctxt,
                Object instance) {
            return delegate.setAndReturn(ctxt, instance, p.getString().toUpperCase());
        }
    }

    public static class Wrapped {
        public String text;
        public int count;
    }

    private static SimpleModule upperCaseModule() {
        return new SimpleModule("upper").setDeserializerModifier(new ValueDeserializerModifier() {
            private static final long serialVersionUID = 1L;

            @Override
            public BeanDeserializerBuilder updateBuilder(DeserializationConfig config,
                    BeanDescription.Supplier beanDescRef, BeanDeserializerBuilder builder) {
                if (beanDescRef.getBeanClass() == Wrapped.class) {
                    SettableBeanProperty text = builder.findProperty(
                            tools.jackson.databind.PropertyName.construct("text"));
                    builder.addOrReplaceProperty(new UpperCaseProperty(text), true);
                }
                return builder;
            }
        });
    }

    @Test
    public void userPropertyWrapperRuns() {
        Mappers m = mappers(b -> b.addModule(upperCaseModule()));
        Wrapped w = assertSameRead(m, "{\"text\":\"xy\",\"count\":2}", Wrapped.class).get();
        assertEquals("XY", w.text);
        String layout = assertGeneratedReader(m.blackbird(), Wrapped.class);
        assertArm(layout, "text=STOCK");
        assertArm(layout, "count=INT");
    }

    /*
    /**********************************************************************
    /* Ignoral sets as stock computes them
    /**********************************************************************
     */

    @JsonIgnoreType
    public static class Secret {
        public String s;
    }

    public static class HasSecret {
        public String name;
        public Secret secret;
    }

    @Test
    public void ignoredTypePropertyIsIgnoredNotUnknown() {
        Mappers m = mappers(b -> b.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
        HasSecret h = assertSameRead(m, "{\"name\":\"n\",\"secret\":{\"s\":\"x\"}}",
                HasSecret.class).get();
        assertEquals("n", h.name);
        assertNull(h.secret);
        assertGeneratedReader(m.blackbird(), HasSecret.class);
    }

    @JsonIgnoreProperties(value = "x", allowSetters = true)
    public static class AllowSetters {
        public String name;
        public String x;
    }

    @Test
    public void allowSettersStillReadsProperty() {
        Mappers m = mappers();
        AllowSetters a = assertSameRead(m, "{\"name\":\"n\",\"x\":\"set\"}",
                AllowSetters.class).get();
        assertEquals("set", a.x);
        assertGeneratedReader(m.blackbird(), AllowSetters.class);
    }

    @JsonIgnoreProperties(value = "y", allowSetters = true)
    public static class AllowSettersUnknown {
        public String name;
    }

    @Test
    public void allowSettersNameWithoutSetterIsUnknown() {
        Mappers m = mappers(b -> b.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
        UnrecognizedPropertyException e = assertSameRead(m, "{\"name\":\"n\",\"y\":1}",
                AllowSettersUnknown.class).error(UnrecognizedPropertyException.class);
        assertEquals("y", e.getPropertyName());
        assertGeneratedReader(m.blackbird(), AllowSettersUnknown.class);
    }

    public record IgnoredComponent(int id, @JsonIgnore String name) {}

    @Test
    public void ignoredRecordComponentKeepsAbsentValue() {
        Mappers m = mappers();
        IgnoredComponent r = assertSameRead(m, "{\"id\":123,\"name\":\"Bob\"}",
                IgnoredComponent.class).get();
        assertEquals(new IgnoredComponent(123, null), r);
        assertGeneratedReader(m.blackbird(), IgnoredComponent.class);
    }

    @Test
    public void ignoredRecordComponentFailsWhenIgnoredPropertiesFail() {
        Mappers m = mappers(b -> b.enable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES));
        assertSameRead(m, "{\"id\":123,\"name\":\"Bob\"}", IgnoredComponent.class)
                .error(tools.jackson.databind.exc.IgnoredPropertyException.class);
    }
}
