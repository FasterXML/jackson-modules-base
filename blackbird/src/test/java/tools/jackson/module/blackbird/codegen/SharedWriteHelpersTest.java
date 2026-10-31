package tools.jackson.module.blackbird.codegen;

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

/**
 * All generated writers call one class of write helpers, padded past the
 * HotSpot inline limits (see {@code BeanWriterGenerator.INLINE_PAD}).
 */
public class SharedWriteHelpersTest extends BlackbirdTestBase
{
    // The HotSpot default of FreqInlineSize (C2) on every platform. The C1
    // limit, C1MaxInlineSize, is 35 bytes.
    private static final int FREQ_INLINE_SIZE = 325;

    private static final List<String> HELPERS = List.of("$bool", "$int", "$long", "$name", "$str");

    public static class Point {
        public String name = "p";
        public int x = 1;
        public long y = 2L;
        public boolean on = true;
        public Point next;
    }

    public static class Tag {
        public String text;
        public int size = 3;
        public Point at = new Point();
    }

    @Test
    public void writersCallOneSharedHelperClass() throws Exception {
        ObjectMapper mapper = newObjectMapper();
        assertGeneratedWriter(mapper, Point.class);
        assertGeneratedWriter(mapper, Tag.class);
        Class<?> pointWriter = rootWriter(mapper, Point.class).getClass();
        Class<?> tagWriter = rootWriter(mapper, Tag.class).getClass();
        assertEquals(List.of(), helperMethods(pointWriter));
        assertEquals(List.of(), helperMethods(tagWriter));

        Class<?> helpers = Class.forName(WriterCodegen.HELPERS_CLASS, false,
                pointWriter.getClassLoader());
        assertFalse(helpers.isHidden());
        assertSame(pointWriter.getModule(), helpers.getModule());
        assertEquals(pointWriter.getPackageName(), helpers.getPackageName());
        assertEquals(HELPERS, helperMethods(helpers));
    }

    @Test
    public void helpersWriteAsStock() {
        Tag tag = new Tag();
        assertEquals(newVanillaJSONMapper().writeValueAsString(tag),
                newObjectMapper().writeValueAsString(tag));
    }

    @Test
    public void helpersExceedTheInlineLimits() {
        ClassModel model = ClassFile.of().parse(ClassFileEmitters.emitWriteHelpers());
        Map<String, Integer> codeLengths = new TreeMap<>();
        for (MethodModel m : model.methods()) {
            codeLengths.put(m.methodName().stringValue(),
                    m.findAttribute(Attributes.code()).orElseThrow().codeLength());
        }
        assertEquals(HELPERS, List.copyOf(codeLengths.keySet()));
        codeLengths.forEach((name, length) -> assertTrue(length > FREQ_INLINE_SIZE,
                name + " has " + length + " bytes of bytecode, so C2 can inline it"));
    }

    // Synthetic methods are left out: a coverage agent such as JaCoCo adds
    // $jacocoInit to the helper class when it is defined.
    private static List<String> helperMethods(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(m -> !m.isSynthetic())
                .map(Method::getName)
                .filter(n -> n.startsWith("$"))
                .sorted()
                .toList();
    }
}
