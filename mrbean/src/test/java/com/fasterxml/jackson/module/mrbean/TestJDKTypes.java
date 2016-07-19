package com.fasterxml.jackson.module.mrbean;

import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tests stemming from [#12], where `Calendar` fails; however, bit more general
 * problem.
 */
public class TestJDKTypes extends BaseTest
{
    private final ObjectMapper MAPPER = newMrBeanMapper();

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
        CharSequence seq = MAPPER.readValue(quote("abc"), CharSequence.class);
        assertEquals("abc", (String) seq);
    }
}
