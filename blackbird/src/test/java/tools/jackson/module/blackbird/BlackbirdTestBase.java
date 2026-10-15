package tools.jackson.module.blackbird;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import tools.jackson.core.*;

import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;

import tools.jackson.module.blackbird.testutil.NoCheckSubTypeValidator;
import tools.jackson.module.blackbird.codegen.CodegenFallbacks;
import tools.jackson.module.blackbird.internal.GeneratedBuilderBase;
import tools.jackson.module.blackbird.internal.GeneratedReaderBase;
import tools.jackson.module.blackbird.internal.GeneratedWriterBase;

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

    // 24-Oct-2017, tatu: Added to check for glitches in serialization unrolling
    @JsonPropertyOrder({ "a", "b", "c", "d", "e", "f" })
    protected static class Pojo6 {
        private String b = "foo";
        private double e = 0.25;

        public Pojo6() { }

        public int a = 13;
        public String getB() { return b; }
        public boolean c = true;
        public long d = -13117L;
        public double getE() { return e; };
        public int[] f = new int[] { 1, 2, 3 };

        public void setB(String v) { b = v; }
        public void setE(double v) { e = v; }
    }

    /**
     * Sample class from Jackson tutorial ("JacksonInFiveMinutes")
     */
    @JsonPropertyOrder(alphabetic=true)
    protected static class FiveMinuteUser {
        public enum Gender { MALE, FEMALE };

	@JsonPropertyOrder(alphabetic=true)
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

    static {
        // The suite runs strict: a codec-generation failure fails the test
        // instead of demoting to the stock path, so the lenient production
        // fallback cannot mask a generator bug here.
        System.setProperty(CodegenFallbacks.FAIL_ON_ERROR_PROPERTY, "true");
    }

    protected static JsonMapper.Builder mapperBuilder() {
        return JsonMapper.builder()
                .polymorphicTypeValidator(new NoCheckSubTypeValidator())
                .addModule(new BlackbirdModule());
    }

    protected static JsonMapper newVanillaJSONMapper() {
        return new JsonMapper();
    }

    /*
    /**********************************************************
    /* Helper methods; codec engagement
    /**********************************************************
     */

    /** The (contextualized) deserializer the mapper uses for a root value of the type. */
    protected static ValueDeserializer<Object> rootReader(ObjectMapper mapper, Class<?> type) {
        DeserializationContext ctxt = mapper._deserializationContext();
        return ctxt.findRootValueDeserializer(ctxt.constructType(type));
    }

    /** The (contextualized) serializer the mapper uses for a root value of the type. */
    protected static ValueSerializer<Object> rootWriter(ObjectMapper mapper, Class<?> type) {
        SerializationContext ctxt = mapper._serializationContext();
        return ctxt.handleRootContextualization(ctxt.findValueSerializer(type));
    }

    /**
     * Whether a (de)serializer that a modifier captured resolved to a
     * generated codec: the placeholder stays in place either way, and only
     * its codec field tells a generated bean from a gated one.
     */
    protected static boolean hasGeneratedCodec(Object captured) {
        try {
            java.lang.reflect.Field codec = captured.getClass().getDeclaredField("_codec");
            codec.setAccessible(true);
            return codec.get(captured) != null;
        } catch (NoSuchFieldException e) {
            return false;
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    /** Asserts that the generated reader handles the type, and returns its arm layout. */
    protected static String assertGeneratedReader(ObjectMapper mapper, Class<?> type) {
        ValueDeserializer<Object> d = rootReader(mapper, type);
        if (d instanceof GeneratedReaderBase r) {
            return r.codecLayout();
        }
        if (d instanceof GeneratedBuilderBase b) {
            return b.codecLayout();
        }
        return fail(type.getSimpleName() + " should use a generated reader, uses " + d);
    }

    /** Asserts that the stock deserializer handles the type. */
    protected static void assertStockReader(ObjectMapper mapper, Class<?> type) {
        ValueDeserializer<Object> d = rootReader(mapper, type);
        assertFalse(d instanceof GeneratedReaderBase || d instanceof GeneratedBuilderBase,
                type.getSimpleName() + " should keep the stock deserializer, uses " + d);
    }

    /** Asserts that the generated writer handles the type, and returns its layout. */
    protected static String assertGeneratedWriter(ObjectMapper mapper, Class<?> type) {
        ValueSerializer<Object> s = rootWriter(mapper, type);
        assertTrue(s instanceof GeneratedWriterBase,
                type.getSimpleName() + " should use a generated writer, uses " + s);
        return ((GeneratedWriterBase) s).codecLayout();
    }

    /** Asserts that the stock serializer handles the type. */
    protected static void assertStockWriter(ObjectMapper mapper, Class<?> type) {
        ValueSerializer<Object> s = rootWriter(mapper, type);
        assertFalse(s instanceof GeneratedWriterBase,
                type.getSimpleName() + " should keep the stock serializer, uses " + s);
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

    /**
     * Method that gets textual contents of the current token using
     * available methods, and ensures results are consistent, before
     * returning them
     */
    protected String getAndVerifyText(JsonParser p)
    {
        // Ok, let's verify other accessors
        int actLen = p.getStringLength();
        char[] ch = p.getStringCharacters();
        String str2 = new String(ch, p.getStringOffset(), actLen);
        String str = p.getString();

        if (str.length() !=  actLen) {
            fail("Internal problem (p.token == "+p.currentToken()+"): p.getText().length() ['"+str+"'] == "+str.length()+"; p.getTextLength() == "+actLen);
        }
        assertEquals("String access via getText(), getTextXxx() must be the same", str, str2);

        return str;
    }

    protected void verifyFieldName(JsonParser p, String expName)
    {
        assertEquals(expName, p.getString());
        assertEquals(expName, p.currentName());
    }

    protected void verifyIntValue(JsonParser p, long expValue)
    {
        // First, via textual
        assertEquals(String.valueOf(expValue), p.getString());
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
        return str.getBytes(StandardCharsets.UTF_8);
    }
}
