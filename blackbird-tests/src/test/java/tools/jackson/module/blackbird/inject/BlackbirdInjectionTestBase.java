package tools.jackson.module.blackbird.inject;

import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.module.blackbird.BlackbirdModule;
import tools.jackson.module.blackbird.internal.GeneratedBuilderBase;
import tools.jackson.module.blackbird.internal.GeneratedReaderBase;
import tools.jackson.module.blackbird.internal.GeneratedWriterBase;

// Test utilities for verifying that Blackbird's codec generation engaged for a
// given POJO loaded from the unnamed module (classpath). The checks look at
// the (de)serializer the mapper uses for the type: a generated codec, or the
// stock one when a gate demoted the bean. (A modifier would only see the
// placeholder that stands in for either until resolution.)
abstract class BlackbirdInjectionTestBase
{
    static {
        // Strict mode: a codec-generation failure fails the test instead of
        // demoting to the stock path (see CodegenFallbacks).
        System.setProperty("tools.jackson.module.blackbird.failOnCodegenError", "true");
    }

    protected static Harness newHarness() {
        return new Harness();
    }

    protected static final class Harness {
        final JsonMapper mapper = JsonMapper.builder()
                .addModule(new BlackbirdModule())
                .build();

        ValueDeserializer<?> deserFor(Class<?> cls) {
            DeserializationContext ctxt = mapper._deserializationContext();
            return ctxt.findContextualValueDeserializer(ctxt.constructType(cls), null);
        }

        ValueSerializer<?> serFor(Class<?> cls) {
            SerializationContext ctxt = mapper._serializationContext();
            return ctxt.handleRootContextualization(ctxt.findValueSerializer(cls));
        }
    }

    /** True if the deserializer is a generated Blackbird codec. */
    protected static boolean isBlackbirdReader(ValueDeserializer<?> deser) {
        return (deser instanceof GeneratedReaderBase) || (deser instanceof GeneratedBuilderBase);
    }

    /** True if the serializer is a generated Blackbird writer. */
    protected static boolean isBlackbirdWriter(ValueSerializer<?> ser) {
        return ser instanceof GeneratedWriterBase;
    }
}
