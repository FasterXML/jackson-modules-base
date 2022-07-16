package com.fasterxml.jackson.module.mrbean;

import java.io.*;
import java.util.Arrays;

import junit.framework.TestCase;

import tools.jackson.core.*;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

//import static org.junit.Assert.*;

public abstract class BaseTest
    extends TestCase
{
    /*
    /**********************************************************
    /* Mapper construction
    /**********************************************************
     */

    protected ObjectMapper newMrBeanMapper() {
        return JsonMapper.builder()
                .addModule(new MrBeanModule())
                .build();
    }

    protected ObjectMapper newPlainJsonMapper() {
        return new JsonMapper();
    }

    /*
    /**********************************************************
    /* Parser/generator construction
    /**********************************************************
     */

    protected JsonParser createParserUsingReader(String input)
    {
        return createParserUsingReader(new JsonFactory(), input);
    }

    protected JsonParser createParserUsingReader(JsonFactory f, String input)
    {
        return f.createParser(ObjectReadContext.empty(), new StringReader(input));
    }

    protected JsonParser createParserUsingStream(String input, String encoding)
    {
        return createParserUsingStream(new JsonFactory(), input, encoding);
    }

    protected JsonParser createParserUsingStream(JsonFactory f,
            String input, String encoding)
    {

        /* 23-Apr-2008, tatus: UTF-32 is not supported by JDK, have to
         *   use our own codec too (which is not optimal since there's
         *   a chance both encoder and decoder might have bugs, but ones
         *   that cancel each other out or such)
         */
        byte[] data;
        if (encoding.equalsIgnoreCase("UTF-32")) {
            data = encodeInUTF32BE(input);
        } else {
            try {
                data = input.getBytes(encoding);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        InputStream is = new ByteArrayInputStream(data);
        return f.createParser(ObjectReadContext.empty(), is);
    }

    /*
    /**********************************************************
    /* Additional assertion methods
    /**********************************************************
     */

    protected void assertToken(JsonToken expToken, JsonToken actToken)
    {
        if (actToken != expToken) {
            fail("Expected token "+expToken+", current token "+actToken);
        }
    }

    protected void assertToken(JsonToken expToken, JsonParser p)
    {
        assertToken(expToken, p.currentToken());
    }

    protected void assertType(Object ob, Class<?> expType)
    {
        if (ob == null) {
            fail("Expected an object of type "+expType.getName()+", got null");
        }
        Class<?> cls = ob.getClass();
        if (!expType.isAssignableFrom(cls)) {
            fail("Expected type "+expType.getName()+", got "+cls.getName());
        }
    }

    protected void verifyException(Throwable e, String... matches)
    {
        String msg = e.getMessage();
        String lmsg = (msg == null) ? "" : msg.toLowerCase();
        for (String match : matches) {
            String lmatch = match.toLowerCase();
            if (lmsg.indexOf(lmatch) >= 0) {
                return;
            }
        }
        fail("Expected an exception with one of substrings ("+Arrays.asList(matches)+"): got one with message \""+msg+"\"");
    }

    /**
     * Method that gets textual contents of the current token using
     * available methods, and ensures results are consistent, before
     * returning them
     */
    protected String getAndVerifyText(JsonParser p)
    {
        // Ok, let's verify other accessors
        int actLen = p.getTextLength();
        char[] ch = p.getTextCharacters();
        String str2 = new String(ch, p.getTextOffset(), actLen);
        String str = p.getText();

        if (str.length() !=  actLen) {
            fail("Internal problem (p.token == "+p.currentToken()+"): p.getText().length() ['"+str+"'] == "+str.length()+"; p.getTextLength() == "+actLen);
        }
        assertEquals("String access via getText(), getTextXxx() must be the same", str, str2);

        return str;
    }

    /*
    /**********************************************************
    /* And other helpers
    /**********************************************************
     */

    protected byte[] encodeInUTF32BE(String input)
    {
        int len = input.length();
        byte[] result = new byte[len * 4];
        int ptr = 0;
        for (int i = 0; i < len; ++i, ptr += 4) {
            char c = input.charAt(i);
            result[ptr] = result[ptr+1] = (byte) 0;
            result[ptr+2] = (byte) (c >> 8);
            result[ptr+3] = (byte) c;
        }
        return result;
    }

    public String q(String str) {
        return '"'+str+'"';
    }
}
