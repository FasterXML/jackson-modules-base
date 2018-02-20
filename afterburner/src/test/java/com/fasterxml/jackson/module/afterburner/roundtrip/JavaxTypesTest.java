package com.fasterxml.jackson.module.afterburner.roundtrip;

import javax.security.auth.AuthPermission;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.afterburner.AfterburnerTestBase;

/**
 * Simple tests to try to see that handling of semi-standard types
 * from javax.* work.
 */
public class JavaxTypesTest extends AfterburnerTestBase
{
    final ObjectMapper MAPPER = newAfterburnerMapper();

    public void testGregorianCalendar() throws Exception
    {
        DatatypeFactory f = DatatypeFactory.newInstance();
        XMLGregorianCalendar in = f.newXMLGregorianCalendar();
        in.setYear(2014);
        in.setMonth(3);
        
        String json = MAPPER.writeValueAsString(in);
        assertNotNull(json);
        XMLGregorianCalendar out = MAPPER.readValue(json, XMLGregorianCalendar.class);
        assertNotNull(out);
        
        // minor sanity check
        assertEquals(in.getYear(), out.getYear());
    }

    public void testAuthPermission() throws Exception
    {
        AuthPermission in = new AuthPermission("foo");
        String json = MAPPER.writeValueAsString(in);
        assertNotNull(json);
        
        // actually, deserialization won't work by default. So let's just check
        // some lexical aspects
        if (!json.contains("\"name\":")) {
            fail("Unexpected JSON, missing 'name' property: "+json);
        }
    }
}
