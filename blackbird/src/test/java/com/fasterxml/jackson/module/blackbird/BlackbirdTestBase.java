package com.fasterxml.jackson.module.blackbird;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;

import com.fasterxml.jackson.core.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

public abstract class BlackbirdTestBase
{
    // // // First some "shared" classes from databind's `BaseMapTest`

    public enum ABC { A, B, C; }

    protected static class BooleanWrapper {
        protected boolean b;

        public BooleanWrapper() { }
        public BooleanWrapper(boolean value) { b = value; }

        public void setB(boolean b0) { b = b0; }
        public boolean getB() { return b; }
    }

    protected static class IntWrapper {
        protected int i;

        public IntWrapper() { }
        public IntWrapper(int value) { i = value; }

        public void setI(int i0) { i = i0; }
        public int getI() { return i; }
    }

    protected static class LongWrapper {
        protected long l;

        public LongWrapper() { }
        public LongWrapper(long value) { l = value; }

        public void setL(long l0) { l = l0; }
        public long getL() { return l; }
    }

    protected static class DoubleWrapper {
        protected double d;

        public DoubleWrapper() { }
        public DoubleWrapper(double value) { d = value; }

        public void setD(double d0) { d = d0; }
        public double getD() { return d; }
    }

    // since 2.8
    public static class Point {
        public int x, y;

        protected Point() { } // for deser
        public Point(int x0, int y0) {
            x = x0;
            y = y0;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Point)) {
                return false;
            }
            Point other = (Point) o;
            return (other.x == x) && (other.y == y);
        }

        @Override
        public String toString() {
            return String.format("[x=%d, y=%d]", x, y);
        }
    }

    /**
     * Sample class from Jackson tutorial ("JacksonInFiveMinutes")
     */
    protected static class FiveMinuteUser {
        public enum Gender { MALE, FEMALE };

        public static class Name
        {
          private String _first, _last;

          public Name() { }
          public Name(String f, String l) {
              _first = f;
              _last = l;
          }

          public String getFirst() { return _first; }
          public String getLast() { return _last; }

          public void setFirst(String s) { _first = s; }
          public void setLast(String s) { _last = s; }

          @Override
          public boolean equals(Object o)
          {
              if (o == this) return true;
              if (o == null || o.getClass() != getClass()) return false;
              Name other = (Name) o;
              return _first.equals(other._first) && _last.equals(other._last);
          }
        }

        private Gender _gender;
        private Name _name;
        private boolean _isVerified;
        private byte[] _userImage;

        public FiveMinuteUser() { }

        public FiveMinuteUser(String first, String last, boolean verified, Gender g, byte[] data)
        {
            _name = new Name(first, last);
            _isVerified = verified;
            _gender = g;
            _userImage = data;
        }

        public Name getName() { return _name; }
        public boolean isVerified() { return _isVerified; }
        public Gender getGender() { return _gender; }
        public byte[] getUserImage() { return _userImage; }

        public void setName(Name n) { _name = n; }
        public void setVerified(boolean b) { _isVerified = b; }
        public void setGender(Gender g) { _gender = g; }
        public void setUserImage(byte[] b) { _userImage = b; }

        @Override
        public boolean equals(Object o)
        {
            if (o == this) return true;
            if (o == null || o.getClass() != getClass()) return false;
            FiveMinuteUser other = (FiveMinuteUser) o;
            if (_isVerified != other._isVerified) return false;
            if (_gender != other._gender) return false;
            if (!_name.equals(other._name)) return false;
            byte[] otherImage = other._userImage;
            if (otherImage.length != _userImage.length) return false;
            for (int i = 0, len = _userImage.length; i < len; ++i) {
                if (_userImage[i] != otherImage[i]) {
                    return false;
                }
            }
            return true;
        }
    }

    protected BlackbirdTestBase() { }

    /*
    /**********************************************************
    /* Factory methods: note, copied from `BaseMapTest`
    /**********************************************************
     */

    private static ObjectMapper SHARED_MAPPER;

    @Deprecated
    protected ObjectMapper objectMapper() {
        if (SHARED_MAPPER == null) {
            SHARED_MAPPER = newObjectMapper();
        }
        return SHARED_MAPPER;
    }

    protected static JsonMapper newObjectMapper() {
        return mapperBuilder().build();
    }

    protected static JsonMapper newBlackbirdMapper() {
        return mapperBuilder().build();
    }

    protected static JsonMapper.Builder blackbirdMapperBuilder() {
        return mapperBuilder();
    }

    protected static JsonMapper.Builder mapperBuilder() {
        return JsonMapper.builder()
                .addModule(new BlackbirdModule(MethodHandles::lookup));
    }

    protected static JsonMapper newVanillaJSONMapper() {
        return new JsonMapper();
    }

    /*
    /**********************************************************
    /* Helper methods; assertions
    /**********************************************************
     */

    protected void assertToken(JsonToken expToken, JsonToken actToken)
    {
        if (actToken != expToken) {
            fail("Expected token "+expToken+", current token "+actToken);
        }
    }

    protected void assertToken(JsonToken expToken, JsonParser jp)
    {
        assertToken(expToken, jp.getCurrentToken());
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

    /**
     * Method that gets textual contents of the current token using
     * available methods, and ensures results are consistent, before
     * returning them
     */
    protected String getAndVerifyText(JsonParser jp)
        throws IOException, JsonParseException
    {
        // Ok, let's verify other accessors
        int actLen = jp.getTextLength();
        char[] ch = jp.getTextCharacters();
        String str2 = new String(ch, jp.getTextOffset(), actLen);
        String str = jp.getText();

        if (str.length() !=  actLen) {
            fail("Internal problem (jp.token == "+jp.getCurrentToken()+"): jp.getText().length() ['"+str+"'] == "+str.length()+"; jp.getTextLength() == "+actLen);
        }
        assertEquals("String access via getText(), getTextXxx() must be the same", str, str2);

        return str;
    }

    protected void verifyFieldName(JsonParser jp, String expName)
        throws IOException
    {
        assertEquals(expName, jp.getText());
        assertEquals(expName, jp.currentName());
    }

    protected void verifyIntValue(JsonParser jp, long expValue)
        throws IOException
    {
        // First, via textual
        assertEquals(String.valueOf(expValue), jp.getText());
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

    /*
    /**********************************************************
    /* Helper methods, other
    /**********************************************************
     */

    public String quote(String str) {
        return q(str);
    }

    public String q(String str) {
        return '"'+str+'"';
    }

    protected static String aposToQuotes(String json) {
        return a2q(json);
    }

    protected static String a2q(String json) {
        return json.replace("'", "\"");
    }
    
    protected byte[] utf8Bytes(String str) {
        try {
            return str.getBytes("UTF-8");
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
