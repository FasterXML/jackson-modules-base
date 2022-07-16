package com.fasterxml.jackson.module.mrbean;

import java.io.Serializable;
import java.util.*;

import com.fasterxml.jackson.annotation.JsonFormat;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * Tests stemming from [#12], where `Calendar` fails; however, bit more general
 * problem.
 */
public class TestJDKTypes extends BaseTest
{
    static class Bean117UsingJsonFormat {
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public int value = 42;
    }

    static class Bean117UsingJsonSerialize {
        @JsonSerialize(using = ToStringSerializer.class)
        public int value = 42;
    }

    private final ObjectMapper MAPPER = newMrBeanMapper();
    private final ObjectMapper VANILLA_MAPPER = newPlainJsonMapper();

    public void testDateTimeTypes() throws Exception
    {
        Calendar cal = MAPPER.readValue("0", Calendar.class);
        assertNotNull(cal);
        assertEquals(0L, cal.getTimeInMillis());

        Date dt = MAPPER.readValue("0", Date.class);
        assertNotNull(dt);
        assertEquals(0L, dt.getTime());
    }

    public void testNumbers() throws Exception
    {
        Number nr = MAPPER.readValue("0", Number.class);
        assertNotNull(nr);
        assertSame(Integer.class, nr.getClass());

        nr = MAPPER.readValue("0.0", Number.class);
        assertNotNull(nr);
        assertSame(Double.class, nr.getClass());
    }

    public void testIterable() throws Exception
    {
        Object ob = MAPPER.readValue("[ ]", Iterable.class);
        assertNotNull(ob);
        assertTrue(ob instanceof List<?>);

        // Let's try with some data as well
        Iterable<?> itrb = MAPPER.readValue("[ 123 ]", Iterable.class);
        assertTrue(itrb instanceof List<?>);
        List<?> l = (List<?>) itrb;
        assertEquals(1, l.size());
        assertEquals(Integer.valueOf(123), l.get(0));
    }

    public void testStringLike() throws Exception
    {
        CharSequence seq = MAPPER.readValue(q("abc"), CharSequence.class);
        assertEquals("abc", (String) seq);
    }

    // [modules-base#74]: more types to skip
    public void testSerializable() throws Exception
    {
//        Serializable value = MAPPER.readValue(quote("abc"), Serializable.class);
        Serializable value = new ObjectMapper().readValue(q("abc"), Serializable.class);
        assertEquals("abc", (String) value);
    }

    // Extra test inspired by Afterburner report
    public void testIntAsString() throws Exception
    {
        final String EXP_JSON = "{\"value\":\"42\"}";

        // First, check usage via `@JsonFormat`
        assertEquals(EXP_JSON, VANILLA_MAPPER.writeValueAsString(new Bean117UsingJsonFormat()));
        assertEquals(EXP_JSON, MAPPER.writeValueAsString(new Bean117UsingJsonFormat()));

        // then with `@JsonSerialize`
        assertEquals(EXP_JSON, VANILLA_MAPPER.writeValueAsString(new Bean117UsingJsonSerialize()));
        assertEquals(EXP_JSON, MAPPER.writeValueAsString(new Bean117UsingJsonSerialize()));
    }

    // [modules-base#132]: Don't block "java.util.TimeZone"
    public void testUtilTimeZone() throws Exception
    {
        final String json = q("PST");

        TimeZone tz1 = VANILLA_MAPPER.readValue(json, TimeZone.class);
        assertNotNull(tz1);

        TimeZone tz2 = MAPPER.readValue(json, TimeZone.class);
        assertNotNull(tz2);

        assertEquals(tz1.getID(), tz2.getID());
    }
}

