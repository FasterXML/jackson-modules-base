package tools.jackson.module.blackbird.deser;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

/**
 * readerForUpdating parity: the generated reader forwards update-value reads
 * to the stock deserializer, so updating must behave exactly like vanilla.
 */
public class BBReaderUpdatingTest extends BlackbirdTestBase
{
    public static class Mutable {
        private int count;
        private String name;
        private String keep;

        public int getCount() { return count; }
        public void setCount(int c) { count = c; }
        public String getName() { return name; }
        public void setName(String n) { name = n; }
        public String getKeep() { return keep; }
        public void setKeep(String k) { keep = k; }
    }

    private final ObjectMapper mapper = newObjectMapper();
    private final ObjectMapper vanilla = newVanillaJSONMapper();

    @Test
    public void testUpdatingReadMatchesVanilla() throws Exception {
        String doc = a2q("{'count':42,'name':'updated'}");

        Mutable mine = new Mutable();
        mine.setKeep("original");
        mine.setName("old");
        Object result = mapper.readerForUpdating(mine).readValue(doc);
        assertSame(mine, result);

        Mutable theirs = new Mutable();
        theirs.setKeep("original");
        theirs.setName("old");
        vanilla.readerForUpdating(theirs).readValue(doc);

        assertEquals(theirs.getCount(), mine.getCount());
        assertEquals(theirs.getName(), mine.getName());
        assertEquals(theirs.getKeep(), mine.getKeep());
        assertEquals("original", mine.getKeep());
    }

    @Test
    public void testPlainReadStillGenerated() throws Exception {
        Mutable m = mapper.readValue(a2q("{'count':7,'name':'n','keep':'k'}"), Mutable.class);
        assertEquals(7, m.getCount());
        assertEquals("n", m.getName());
        assertEquals("k", m.getKeep());
    }
}
