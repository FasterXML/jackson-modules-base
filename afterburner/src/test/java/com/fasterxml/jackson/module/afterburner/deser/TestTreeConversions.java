package com.fasterxml.jackson.module.afterburner.deser;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.afterburner.AfterburnerTestBase;

public class TestTreeConversions extends AfterburnerTestBase
{
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Occupancy {
        private Integer max;
        private Guests adults;
        private Guests children;

        public Occupancy() {
        }

        public Occupancy(Integer max, Guests adults, Guests children) {
            this.max = max;
            this.adults = adults;
            this.children = children;
        }

        public Integer getMax() {
            return max;
        }

        public Guests getAdults() {
            return adults;
        }

        public Guests getChildren() {
            return children;
        }

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Guests {

        private Integer min;
        private Integer max;

        public Guests() {
        }

        public Guests(Integer min, Integer max) {
            this.min = min;
            this.max = max;
        }

        public Integer getMin() {
            return min;
        }

        public Integer getMax() {
            return max;
        }

    }
    /*
    /**********************************************************
    /* Actual tests
    /**********************************************************
     */

    private final ObjectMapper MAPPER = newAfterburnerMapper();

    public void testConversion() throws Exception
    {
        final JsonNode node = MAPPER.readTree("{" +
                    "\"max\":3," +
                    "\"adults\": {" +
                        "\"min\":1" +
                    "}," +
                    "\"children\":{" +
                        "\"min\":1," +
                        "\"max\":2" +
                    "}" +
                "}");

        final Occupancy occupancy = MAPPER.readerFor(Occupancy.class).readValue(node);

        assertNull(occupancy.getAdults().getMax());
        assertEquals(Integer.valueOf(2), occupancy.getChildren().getMax());
    }
}
