package tools.jackson.module.afterburner.ser;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.module.afterburner.AfterburnerTestBase;

import static org.junit.jupiter.api.Assertions.*;

// [modules-base#314]: overloaded method with same name as getter causes
// "Multiple definitions of method found" from ByteBuddy
public class OverloadedMethodSerTest extends AfterburnerTestBase
{
    // Case 1: static overload with different parameter count
    @JsonPropertyOrder({ "content", "whateverContent" })
    static class StaticOverloadDto {
        private String content;

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getWhateverContent() {
            return getContent("whatever");
        }

        // static "overload": same name, different signature
        public static String getContent(String mystring) {
            return mystring;
        }
    }

    // Case 2: instance overload with different parameter count (non-static)
    @JsonPropertyOrder({ "value", "computed" })
    static class InstanceOverloadDto {
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getComputed() {
            return getValue("prefix");
        }

        // Non-static overload: same name, different parameter count
        public String getValue(String prefix) {
            return prefix + "-" + value;
        }
    }

    private final ObjectMapper MAPPER = newAfterburnerMapper();

    // [modules-base#314]: static overload should not confuse ByteBuddy method lookup
    @Test
    void testSerializeWithStaticOverload() throws Exception
    {
        StaticOverloadDto dto = new StaticOverloadDto();
        dto.setContent("mycontent");

        String json = MAPPER.writeValueAsString(dto);
        assertEquals("{\"content\":\"mycontent\",\"whateverContent\":\"whatever\"}", json);
    }

    @Test
    void testDeserializeWithStaticOverload() throws Exception
    {
        StaticOverloadDto dto = MAPPER.readValue(
                "{\"content\":\"mycontent\"}", StaticOverloadDto.class);
        assertEquals("mycontent", dto.getContent());
    }

    // [modules-base#314]: instance overload (different param count) should also work
    @Test
    void testSerializeWithInstanceOverload() throws Exception
    {
        InstanceOverloadDto dto = new InstanceOverloadDto();
        dto.setValue("test");

        String json = MAPPER.writeValueAsString(dto);
        assertEquals("{\"value\":\"test\",\"computed\":\"prefix-test\"}", json);
    }

    @Test
    void testDeserializeWithInstanceOverload() throws Exception
    {
        InstanceOverloadDto dto = MAPPER.readValue(
                "{\"value\":\"test\"}", InstanceOverloadDto.class);
        assertEquals("test", dto.getValue());
    }
}
