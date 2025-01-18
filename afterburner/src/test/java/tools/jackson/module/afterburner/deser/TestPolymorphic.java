package tools.jackson.module.afterburner.deser;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.*;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.module.afterburner.AfterburnerTestBase;

import static org.junit.jupiter.api.Assertions.*;

public class TestPolymorphic extends AfterburnerTestBase
{
    static class Envelope {
        @JsonTypeInfo(use=JsonTypeInfo.Id.CLASS, include=JsonTypeInfo.As.EXTERNAL_PROPERTY, property="class")
        Object payload;

        public Envelope(@JsonProperty("payload") Object payload) {
            this.payload = payload;
        }
        public Envelope() { }

        @JsonProperty
        public Object getPayload() {
            return payload;
        }
    }

    static class Payload {
        private String something;

        public Payload(@JsonProperty("something") String something) {
            this.something = something;
        }
        @JsonProperty
        public Object getSomething() {
            return something;
        }
    }

    private final ObjectMapper MAPPER = newAfterburnerMapper();

    @Test
    public void testBasicPolymorphic() throws Exception
    {
        Envelope envelope = new Envelope(new Payload("test"));
        String json = MAPPER.writeValueAsString(envelope);
        Envelope result = MAPPER.readValue(json, Envelope.class);

        assertNotNull(result);
        assertNotNull(result.payload);
        assertEquals(Payload.class, result.payload.getClass());
    }

    // for [module-afterburner#58]; although seems to be due to databind issue
    @Test
    public void testPolymorphicIssue58() throws Exception
    {
        final String CLASS = Payload.class.getName();

        // First, case that has been working always
        final String successCase = "{\"payload\":{\"something\":\"test\"},\"class\":\""+CLASS+"\"}";
        Envelope envelope1 = MAPPER.readValue(successCase, Envelope.class);
        assertNotNull(envelope1);
        assertEquals(Payload.class, envelope1.payload.getClass());

        // and then re-ordered case that was problematic
        final String failCase = "{\"class\":\""+CLASS+"\",\"payload\":{\"something\":\"test\"}}";
        Envelope envelope2 = MAPPER.readValue(failCase, Envelope.class);
        assertNotNull(envelope2);
        assertEquals(Payload.class, envelope2.payload.getClass());
    }
}
