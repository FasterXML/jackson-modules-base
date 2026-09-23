package tools.jackson.module.blackbird.deser;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.deser.std.DelegatingDeserializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PROPERTY_NAME entry through generated readers. The AsProperty polymorphic
 * path hands a subtype deserializer a stream positioned on the property after
 * the type id (or a buffered-replay sequence starting on one), so generated
 * readers accept that entry natively instead of delegating. Every behavior
 * case compares against a vanilla mapper.
 */
public class BBReaderEntryTest extends BlackbirdTestBase
{
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = Dog.class, name = "dog"),
        @JsonSubTypes.Type(value = RecCat.class, name = "cat")
    })
    public interface Animal { }

    public static class Dog implements Animal {
        public String name;
        public int age;
        public boolean good;
    }

    public record RecCat(@JsonProperty(required = true) String name, int lives)
            implements Animal { }

    public static class Plain {
        public String s;
        public int i;
    }

    private final ObjectMapper mapper = newObjectMapper();
    private final ObjectMapper vanilla = newVanillaJSONMapper();

    // Pins the databind contract the native entry depends on: the AsProperty
    // type deserializer invokes the subtype deserializer with the stream on
    // PROPERTY_NAME, for the id-first (live stream) and id-last (buffered
    // sequence) orders both. Runs against vanilla, no Blackbird.
    @Test
    public void databindHandsPropertyNameEntry() throws Exception
    {
        ConcurrentMap<String, JsonToken> entries = new ConcurrentHashMap<>();
        ObjectMapper spied = JsonMapper.builder()
                .addModule(new SimpleModule("spy") {
                    private static final long serialVersionUID = 1L;
                    @Override
                    public void setupModule(SetupContext ctxt) {
                        super.setupModule(ctxt);
                        ctxt.addDeserializerModifier(new ValueDeserializerModifier() {
                            private static final long serialVersionUID = 1L;
                            @Override
                            public ValueDeserializer<?> modifyDeserializer(
                                    DeserializationConfig config,
                                    BeanDescription.Supplier beanDescRef,
                                    ValueDeserializer<?> deser) {
                                if (beanDescRef.getBeanClass() != Dog.class) {
                                    return deser;
                                }
                                return new EntrySpy(deser, entries);
                            }
                        });
                    }
                })
                .build();

        spied.readValue(a2q("{'type':'dog','name':'Rex','age':3,'good':true}"), Animal.class);
        assertEquals(JsonToken.PROPERTY_NAME, entries.get("entry"), "id-first entry");

        spied.readValue(a2q("{'name':'Rex','age':3,'good':true,'type':'dog'}"), Animal.class);
        assertEquals(JsonToken.PROPERTY_NAME, entries.get("entry"), "id-last (buffered) entry");
    }

    @Test
    public void polymorphicTypeIdFirst() throws Exception
    {
        String doc = a2q("{'type':'dog','name':'Rex','age':3,'good':true}");
        Dog d = (Dog) mapper.readValue(doc, Animal.class);
        Dog v = (Dog) vanilla.readValue(doc, Animal.class);
        assertEquals(v.name, d.name);
        assertEquals(v.age, d.age);
        assertEquals(v.good, d.good);
    }

    @Test
    public void polymorphicTypeIdLast() throws Exception
    {
        String doc = a2q("{'name':'Rex','age':3,'good':true,'type':'dog'}");
        Dog d = (Dog) mapper.readValue(doc, Animal.class);
        Dog v = (Dog) vanilla.readValue(doc, Animal.class);
        assertEquals(v.name, d.name);
        assertEquals(v.age, d.age);
        assertEquals(v.good, d.good);
    }

    @Test
    public void polymorphicRecordSubtype() throws Exception
    {
        String doc = a2q("{'type':'cat','name':'Mia','lives':9}");
        assertEquals(vanilla.readValue(doc, Animal.class), mapper.readValue(doc, Animal.class));

        String middle = a2q("{'name':'Mia','type':'cat','lives':9}");
        assertEquals(vanilla.readValue(middle, Animal.class),
                mapper.readValue(middle, Animal.class));

        // Required component missing: same failure both stacks, through the
        // PROPERTY_NAME entry.
        String missing = a2q("{'type':'cat','lives':9}");
        MismatchedInputException expected = assertThrows(MismatchedInputException.class,
                () -> vanilla.readValue(missing, Animal.class));
        MismatchedInputException actual = assertThrows(MismatchedInputException.class,
                () -> mapper.readValue(missing, Animal.class));
        assertEquals(expected.getClass(), actual.getClass());
    }

    @Test
    public void polymorphicIdOnlyEmptyRemainder() throws Exception
    {
        // Nothing after the type id: the subtype deserializer enters at
        // END_OBJECT, which stays on the stock delegation path.
        String doc = a2q("{'type':'dog'}");
        Dog d = (Dog) mapper.readValue(doc, Animal.class);
        Dog v = (Dog) vanilla.readValue(doc, Animal.class);
        assertEquals(v.name, d.name);
        assertEquals(v.age, d.age);
    }

    @Test
    public void readValueAtPropertyName() throws Exception
    {
        String doc = a2q("{'s':'x','i':7}");
        Plain p1 = readPositioned(mapper, doc);
        Plain p2 = readPositioned(vanilla, doc);
        assertEquals(p2.s, p1.s);
        assertEquals(p2.i, p1.i);
    }

    @Test
    public void unknownFirstNameAtEntry() throws Exception
    {
        String doc = a2q("{'mystery':[1,2,{'x':3}],'s':'x','i':7}");
        Plain p1 = readPositioned(mapper, doc);
        Plain p2 = readPositioned(vanilla, doc);
        assertEquals(p2.s, p1.s);
        assertEquals(p2.i, p1.i);
    }

    @Test
    public void polymorphicList() throws Exception
    {
        String doc = a2q("[{'type':'dog','name':'a','age':1,'good':false},"
                + "{'type':'cat','name':'b','lives':2}]");
        List<Animal> got = mapper.readerForListOf(Animal.class).readValue(doc);
        List<Animal> want = vanilla.readerForListOf(Animal.class).readValue(doc);
        assertEquals(want.size(), got.size());
        assertEquals(((Dog) want.get(0)).name, ((Dog) got.get(0)).name);
        assertEquals(want.get(1), got.get(1));
    }

    static class EntrySpy extends DelegatingDeserializer {
        private final ConcurrentMap<String, JsonToken> entries;

        EntrySpy(ValueDeserializer<?> d, ConcurrentMap<String, JsonToken> entries) {
            super(d);
            this.entries = entries;
        }

        @Override
        protected ValueDeserializer<?> newDelegatingInstance(ValueDeserializer<?> newDelegatee) {
            return new EntrySpy(newDelegatee, entries);
        }

        @Override
        public Object deserialize(JsonParser p, DeserializationContext ctxt) {
            entries.put("entry", p.currentToken());
            return super.deserialize(p, ctxt);
        }
    }

    private static Plain readPositioned(ObjectMapper m, String doc) throws Exception
    {
        try (JsonParser p = m.createParser(doc)) {
            assertEquals(JsonToken.START_OBJECT, p.nextToken());
            assertEquals(JsonToken.PROPERTY_NAME, p.nextToken());
            return m.readValue(p, Plain.class);
        }
    }
}
