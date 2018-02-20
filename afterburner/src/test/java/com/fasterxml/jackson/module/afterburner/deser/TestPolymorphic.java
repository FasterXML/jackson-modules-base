package com.fasterxml.jackson.module.afterburner.deser;

import com.fasterxml.jackson.annotation.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.module.afterburner.AfterburnerTestBase;

public class TestPolymorphic extends AfterburnerTestBase
{
    static class Envelope {
        @JsonTypeInfo(use=JsonTypeInfo.Id.CLASS, include=JsonTypeInfo.As.EXTERNAL_PROPERTY, property="class")
        private Object payload;

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

    public void testAfterburner() throws Exception {
        ObjectMapper mapper = newAfterburnerMapper();
        Envelope envelope = new Envelope(new Payload("test"));
        String json = mapper.writeValueAsString(envelope);
        Envelope result = mapper.readValue(json, Envelope.class);

        assertNotNull(result);
        assertNotNull(result.payload);
        assertEquals(Payload.class, result.payload.getClass());
    }

    // for [module-afterburner#58]; although seems to be due to databind issue
    public void testPolymorphicIssue58() throws Exception
    {
        final String CLASS = Payload.class.getName();

        ObjectMapper mapper = newAfterburnerMapper();

        // First, case that has been working always
        final String successCase = "{\"payload\":{\"something\":\"test\"},\"class\":\""+CLASS+"\"}";
        Envelope envelope1 = mapper.readValue(successCase, Envelope.class);
        assertNotNull(envelope1);
        assertEquals(Payload.class, envelope1.payload.getClass());

        // and then re-ordered case that was problematic
        final String failCase = "{\"class\":\""+CLASS+"\",\"payload\":{\"something\":\"test\"}}";
        Envelope envelope2 = mapper.readValue(failCase, Envelope.class);
        assertNotNull(envelope2);
        assertEquals(Payload.class, envelope2.payload.getClass());
    }
}
