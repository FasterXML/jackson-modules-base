package tools.jackson.module.blackbird.deser;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.ValueSerializerModifier;
import tools.jackson.module.blackbird.BlackbirdModule;
import tools.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Non-public classes accelerate: package-private, protected, and private
 * static nested beans (and their non-private members) get generated codecs.
 * Member access rides databind's fixAccess through unreflected constant
 * handles, so the generated class never names the bean and accessibility
 * never gates a bean stock databind handles. Every behavioral check compares
 * against a vanilla mapper.
 */
public class PackagePrivateBeanTest extends BlackbirdTestBase
{
    static class PkgBean {
        private int count;
        private String name;

        public int getCount() { return count; }
        public void setCount(int v) { count = v; }
        public String getName() { return name; }
        public void setName(String v) { name = v; }
    }

    static class PkgMembersBean {
        int direct;
        private long viaSetter;

        int getDirect() { return direct; }
        void setDirect(int v) { direct = v; }
        long getViaSetter() { return viaSetter; }
        void setViaSetter(long v) { viaSetter = v; }
    }

    private static class PrivateBean {
        public int x;
        private String s;

        // Explicit annotations: private accessors are not auto-detected, and
        // this pin is exactly about reaching them once databind selects them.
        @JsonProperty("s")
        private String getS() { return s; }
        @JsonProperty("s")
        private void setS(String v) { s = v; }
    }

    protected static class ProtectedBean {
        public int x;
        public String s;
    }

    record PkgRecord(@JsonProperty(required = true) String id, int qty) { }

    static class Capture {
        final Map<Class<?>, ValueDeserializer<?>> desers = new ConcurrentHashMap<>();
        final Map<Class<?>, ValueSerializer<?>> sers = new ConcurrentHashMap<>();
    }

    private static ObjectMapper mapperWith(Capture capture) {
        SimpleModule captureModule = new SimpleModule("capture") {
            private static final long serialVersionUID = 1L;
            @Override
            public void setupModule(SetupContext ctxt) {
                super.setupModule(ctxt);
                ctxt.addDeserializerModifier(new ValueDeserializerModifier() {
                    private static final long serialVersionUID = 1L;
                    @Override
                    public ValueDeserializer<?> modifyDeserializer(DeserializationConfig cfg,
                            BeanDescription.Supplier ref, ValueDeserializer<?> d) {
                        capture.desers.put(ref.getBeanClass(), d);
                        return d;
                    }
                });
                ctxt.addSerializerModifier(new ValueSerializerModifier() {
                    private static final long serialVersionUID = 1L;
                    @Override
                    public ValueSerializer<?> modifySerializer(SerializationConfig cfg,
                            BeanDescription.Supplier ref, ValueSerializer<?> s) {
                        capture.sers.put(ref.getBeanClass(), s);
                        return s;
                    }
                });
            }
        };
        return JsonMapper.builder()
                .addModule(captureModule)
                .addModule(new BlackbirdModule())
                .build();
    }

    // The placeholder resolves its codec at resolve time; a null codec means
    // the generator gated the bean, so the assertion distinguishes "engaged
    // and generated" from "engaged but silently demoted".
    private static void assertCodecGenerated(Object placeholder) throws Exception {
        assertEquals("BBReaderPlaceholder", placeholder.getClass().getSimpleName(),
                "expected the deserializer placeholder, got "
                        + placeholder.getClass().getName());
        Field codec = placeholder.getClass().getDeclaredField("_codec");
        codec.setAccessible(true);
        assertNotNull(codec.get(placeholder), "placeholder engaged but no codec generated");
    }

    private static void assertWriterGenerated(Object placeholder) throws Exception {
        assertEquals("BBWriterPlaceholder", placeholder.getClass().getSimpleName(),
                "expected the serializer placeholder, got "
                        + placeholder.getClass().getName());
        Field codec = placeholder.getClass().getDeclaredField("_codec");
        codec.setAccessible(true);
        assertNotNull(codec.get(placeholder), "placeholder engaged but no writer generated");
    }

    @Test
    public void testPackagePrivateBeanAccelerates() throws Exception {
        Capture capture = new Capture();
        ObjectMapper mapper = mapperWith(capture);
        ObjectMapper vanilla = newVanillaJSONMapper();

        String doc = a2q("{'count':7,'name':'x'}");
        PkgBean act = mapper.readValue(doc, PkgBean.class);
        PkgBean exp = vanilla.readValue(doc, PkgBean.class);
        assertEquals(exp.getCount(), act.getCount());
        assertEquals(exp.getName(), act.getName());
        assertEquals(vanilla.writeValueAsString(exp), mapper.writeValueAsString(act));

        assertCodecGenerated(capture.desers.get(PkgBean.class));
        assertWriterGenerated(capture.sers.get(PkgBean.class));
    }

    @Test
    public void testPackagePrivateMembersAccelerate() throws Exception {
        Capture capture = new Capture();
        ObjectMapper mapper = mapperWith(capture);
        ObjectMapper vanilla = newVanillaJSONMapper();

        String doc = a2q("{'direct':3,'viaSetter':9000000000}");
        PkgMembersBean act = mapper.readValue(doc, PkgMembersBean.class);
        PkgMembersBean exp = vanilla.readValue(doc, PkgMembersBean.class);
        assertEquals(exp.getDirect(), act.getDirect());
        assertEquals(exp.getViaSetter(), act.getViaSetter());
        assertEquals(vanilla.writeValueAsString(exp), mapper.writeValueAsString(act));

        assertCodecGenerated(capture.desers.get(PkgMembersBean.class));
    }

    @Test
    public void testPrivateClassAccelerates() throws Exception {
        // Widened from a demotion pin: private classes, private setters, and
        // the private constructor all reach the codec through fixAccess'd
        // handles now.
        Capture capture = new Capture();
        ObjectMapper mapper = mapperWith(capture);
        ObjectMapper vanilla = newVanillaJSONMapper();

        String doc = a2q("{'x':5,'s':'in'}");
        PrivateBean act = mapper.readValue(doc, PrivateBean.class);
        PrivateBean exp = vanilla.readValue(doc, PrivateBean.class);
        assertEquals(exp.x, act.x);
        assertEquals(exp.getS(), act.getS());
        assertEquals("in", act.getS());
        assertEquals(vanilla.writeValueAsString(exp), mapper.writeValueAsString(act));

        assertCodecGenerated(capture.desers.get(PrivateBean.class));
        assertWriterGenerated(capture.sers.get(PrivateBean.class));
    }

    @Test
    public void testProtectedNestedBeanAccelerates() throws Exception {
        Capture capture = new Capture();
        ObjectMapper mapper = mapperWith(capture);
        ObjectMapper vanilla = newVanillaJSONMapper();

        String doc = a2q("{'x':1,'s':'y'}");
        ProtectedBean act = mapper.readValue(doc, ProtectedBean.class);
        assertEquals(1, act.x);
        assertEquals("y", act.s);
        assertEquals(vanilla.writeValueAsString(act), mapper.writeValueAsString(act));

        assertCodecGenerated(capture.desers.get(ProtectedBean.class));
    }

    @Test
    public void testPackagePrivateRecord() throws Exception {
        Capture capture = new Capture();
        ObjectMapper mapper = mapperWith(capture);
        ObjectMapper vanilla = newVanillaJSONMapper();

        String doc = a2q("{'id':'a','qty':2}");
        assertEquals(vanilla.readValue(doc, PkgRecord.class),
                mapper.readValue(doc, PkgRecord.class));
        assertCodecGenerated(capture.desers.get(PkgRecord.class));

        MismatchedInputException e = assertThrows(MismatchedInputException.class,
                () -> mapper.readValue(a2q("{'qty':2}"), PkgRecord.class));
        assertTrue(e.getMessage().contains("Missing required creator property 'id'"),
                e.getMessage());
    }
}
