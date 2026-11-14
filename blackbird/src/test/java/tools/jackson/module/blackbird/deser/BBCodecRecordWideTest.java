package tools.jackson.module.blackbird.deser;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import tools.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wide records keep the generated reader. A record whose deserialize method
 * would be too large with the absent-value fill gets a reader without it.
 */
public class BBCodecRecordWideTest extends BlackbirdTestBase
{
    public record Ints40(int i0, int i1, int i2, int i3, int i4, int i5, int i6, int i7, int i8, int i9, int i10, int i11, int i12, int i13, int i14, int i15, int i16, int i17, int i18, int i19, int i20, int i21, int i22, int i23, int i24, int i25, int i26, int i27, int i28, int i29, int i30, int i31, int i32, int i33, int i34, int i35, int i36, int i37, int i38, int i39) {}

    public record Ints48(int i0, int i1, int i2, int i3, int i4, int i5, int i6, int i7, int i8, int i9, int i10, int i11, int i12, int i13, int i14, int i15, int i16, int i17, int i18, int i19, int i20, int i21, int i22, int i23, int i24, int i25, int i26, int i27, int i28, int i29, int i30, int i31, int i32, int i33, int i34, int i35, int i36, int i37, int i38, int i39, int i40, int i41, int i42, int i43, int i44, int i45, int i46, int i47) {}

    public record Strings64(String s0, String s1, String s2, String s3, String s4, String s5, String s6, String s7, String s8, String s9, String s10, String s11, String s12, String s13, String s14, String s15, String s16, String s17, String s18, String s19, String s20, String s21, String s22, String s23, String s24, String s25, String s26, String s27, String s28, String s29, String s30, String s31, String s32, String s33, String s34, String s35, String s36, String s37, String s38, String s39, String s40, String s41, String s42, String s43, String s44, String s45, String s46, String s47, String s48, String s49, String s50, String s51, String s52, String s53, String s54, String s55, String s56, String s57, String s58, String s59, String s60, String s61, String s62, String s63) {}

    private static final ObjectMapper MAPPER = newBlackbirdMapper();
    private static final ObjectMapper VANILLA = newVanillaJSONMapper();

    @Test
    public void testWideRecordsGenerate() {
        assertGeneratedReader(MAPPER, Ints40.class);
        assertGeneratedReader(MAPPER, Ints48.class);
        assertGeneratedReader(MAPPER, Strings64.class);
        assertTrue(hasAbsentFill(Ints40.class));
        assertFalse(hasAbsentFill(Ints48.class));
        assertTrue(hasAbsentFill(Strings64.class));
    }

    @Test
    public void testWideRecordsMissingComponents() throws Exception {
        for (String doc : new String[] { "{}", "{\"i0\":1,\"i39\":2}" }) {
            assertEquals(VANILLA.readValue(doc, Ints40.class), MAPPER.readValue(doc, Ints40.class));
            assertEquals(VANILLA.readValue(doc, Ints48.class), MAPPER.readValue(doc, Ints48.class));
        }
        for (String doc : new String[] { "{}", "{\"s0\":\"a\",\"s63\":\"b\"}" }) {
            assertEquals(VANILLA.readValue(doc, Strings64.class),
                    MAPPER.readValue(doc, Strings64.class));
        }
    }

    // When the generated class fills missing components itself, it has one
    // absent-value method per component that is not required.
    private static boolean hasAbsentFill(Class<?> type) {
        return Arrays.stream(rootReader(MAPPER, type).getClass().getDeclaredMethods())
                .anyMatch(m -> m.getName().startsWith("absent$"));
    }
}
