package tools.jackson.module.blackbird.ser;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Polymorphic serialization parity: databind calls serializeWithType on the
 * subtype's (generated) serializer, which forwards whole-call to the stock
 * serializer, so output must equal vanilla for every inclusion mechanism.
 */
public class BBWriterPolymorphicTest extends BlackbirdTestBase
{
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = Cat.class, name = "cat"),
        @JsonSubTypes.Type(value = Dog.class, name = "dog")
    })
    public static abstract class Animal {
        public String name;
    }

    public static class Cat extends Animal {
        public int lives;
        public boolean indoor;

        static Cat of(String name, int lives, boolean indoor) {
            Cat c = new Cat();
            c.name = name;
            c.lives = lives;
            c.indoor = indoor;
            return c;
        }
    }

    public static class Dog extends Animal {
        public long barks;
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
    @JsonSubTypes({
        @JsonSubTypes.Type(value = WrappedLeaf.class, name = "leaf")
    })
    public static abstract class Wrapped {
    }

    public static class WrappedLeaf extends Wrapped {
        public String label;
        public int size;

        static WrappedLeaf of(String label, int size) {
            WrappedLeaf w = new WrappedLeaf();
            w.label = label;
            w.size = size;
            return w;
        }
    }

    public static class Zoo {
        public String city;
        public Animal star;
    }

    private final ObjectMapper mapper = newObjectMapper();
    private final ObjectMapper vanilla = newVanillaJSONMapper();

    @Test
    public void testPropertyInclusionRootWrite() throws Exception {
        Animal cat = Cat.of("mia", 9, true);
        assertEquals(vanilla.writerFor(Animal.class).writeValueAsString(cat),
                mapper.writerFor(Animal.class).writeValueAsString(cat));
    }

    @Test
    public void testPropertyInclusionNestedWrite() throws Exception {
        Zoo zoo = new Zoo();
        zoo.city = "sf";
        zoo.star = Cat.of("mia", 9, false);
        assertEquals(vanilla.writeValueAsString(zoo), mapper.writeValueAsString(zoo));
    }

    @Test
    public void testPropertyInclusionListWrite() throws Exception {
        List<Animal> animals = List.of(Cat.of("a", 1, true), new Dog());
        assertEquals(vanilla.writerFor(vanilla.getTypeFactory()
                        .constructCollectionType(List.class, Animal.class))
                        .writeValueAsString(animals),
                mapper.writerFor(mapper.getTypeFactory()
                        .constructCollectionType(List.class, Animal.class))
                        .writeValueAsString(animals));
    }

    @Test
    public void testWrapperObjectInclusionWrite() throws Exception {
        Wrapped w = WrappedLeaf.of("x", 3);
        assertEquals(vanilla.writerFor(Wrapped.class).writeValueAsString(w),
                mapper.writerFor(Wrapped.class).writeValueAsString(w));
    }

    @Test
    public void testPolymorphicRoundTrip() throws Exception {
        Animal cat = Cat.of("mia", 9, true);
        String doc = mapper.writerFor(Animal.class).writeValueAsString(cat);
        Animal back = mapper.readValue(doc, Animal.class);
        assertInstanceOf(Cat.class, back);
        assertEquals("mia", back.name);
        assertEquals(9, ((Cat) back).lives);
    }
}
