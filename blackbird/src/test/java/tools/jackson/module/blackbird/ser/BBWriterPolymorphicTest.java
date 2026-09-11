package tools.jackson.module.blackbird.ser;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeId;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.ValueSerializerModifier;
import tools.jackson.module.blackbird.BlackbirdModule;
import tools.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Polymorphic serialization parity: databind calls serializeWithType on the
 * subtype's generated serializer, whose native override replicates stock
 * BeanSerializerBase's WritableTypeId flow (typeId, writeTypePrefix,
 * assignCurrentValue, properties, writeTypeSuffix), so output must equal
 * vanilla for every inclusion mechanism. Beans with a @JsonTypeId property
 * keep the whole-call forwarding to stock.
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

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_ARRAY)
    @JsonSubTypes({
        @JsonSubTypes.Type(value = ArrayLeaf.class, name = "leaf")
    })
    public static abstract class ArrayWrapped {
    }

    public static class ArrayLeaf extends ArrayWrapped {
        public String label;
        public long count;
    }

    @Test
    public void testWrapperArrayInclusionWrite() throws Exception {
        ArrayLeaf leaf = new ArrayLeaf();
        leaf.label = "x";
        leaf.count = 12;
        assertEquals(vanilla.writerFor(ArrayWrapped.class).writeValueAsString(leaf),
                mapper.writerFor(ArrayWrapped.class).writeValueAsString(leaf));
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = TypeIdLeaf.class, name = "unused")
    })
    public static abstract class TypeIdBase {
    }

    public static class TypeIdLeaf extends TypeIdBase {
        @JsonTypeId
        public String kind = "custom-id";
        public int n;
    }

    @Test
    public void testJsonTypeIdPropertyWrite() throws Exception {
        // @JsonTypeId beans keep the base forwarding (the property value
        // replaces the resolver's id); output still equals vanilla.
        TypeIdLeaf leaf = new TypeIdLeaf();
        leaf.n = 5;
        String v = vanilla.writerFor(TypeIdBase.class).writeValueAsString(leaf);
        assertEquals(v, mapper.writerFor(TypeIdBase.class).writeValueAsString(leaf));
        assertTrue(v.contains("custom-id"), v);
    }

    // The engaged writer must actually generate a codec: byte-equal output
    // alone cannot distinguish native poly writes from forwarding.
    @Test
    public void testSubtypeWriterGeneratesCodec() throws Exception {
        Map<Class<?>, ValueSerializer<?>> seen = new ConcurrentHashMap<>();
        SimpleModule capture = new SimpleModule("capture") {
            private static final long serialVersionUID = 1L;
            @Override
            public void setupModule(SetupContext ctxt) {
                super.setupModule(ctxt);
                ctxt.addSerializerModifier(new ValueSerializerModifier() {
                    private static final long serialVersionUID = 1L;
                    @Override
                    public ValueSerializer<?> modifySerializer(SerializationConfig cfg,
                            BeanDescription.Supplier ref, ValueSerializer<?> s) {
                        seen.put(ref.getBeanClass(), s);
                        return s;
                    }
                });
            }
        };
        ObjectMapper capturing = JsonMapper.builder()
                .addModule(capture)
                .addModule(new BlackbirdModule())
                .build();
        Animal cat = Cat.of("mia", 9, true);
        assertEquals(vanilla.writerFor(Animal.class).writeValueAsString(cat),
                capturing.writerFor(Animal.class).writeValueAsString(cat));
        ValueSerializer<?> captured = seen.get(Cat.class);
        assertNotNull(captured, "no serializer captured for Cat");
        assertEquals("BBWriterPlaceholder", captured.getClass().getSimpleName(),
                "poly subtype did not engage a codec");
        Field codec = captured.getClass().getDeclaredField("_codec");
        codec.setAccessible(true);
        assertNotNull(codec.get(captured), "poly subtype engaged but no writer generated");
    }
}
