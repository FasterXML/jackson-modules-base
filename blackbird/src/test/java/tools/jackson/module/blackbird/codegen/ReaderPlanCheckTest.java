package tools.jackson.module.blackbird.codegen;

import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.module.blackbird.BlackbirdTestBase;
import tools.jackson.module.blackbird.codegen.ReaderCodegen.Construction;
import tools.jackson.module.blackbird.codegen.ReaderCodegen.GenProp;
import tools.jackson.module.blackbird.codegen.ReaderCodegen.Kind;
import tools.jackson.module.blackbird.codegen.ReaderCodegen.ViewStrategy;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Outside record mode, only STOCK arms go without a generated store. A
 * factory that breaks this fails generation, so strict-mode tests report it,
 * instead of the arm quietly taking the stock path.
 */
public class ReaderPlanCheckTest extends BlackbirdTestBase
{
    public static class Bean {
        public int x;
    }

    @Test
    public void scalarArmWithoutStoreFails() {
        for (Kind kind : new Kind[] { Kind.STRING, Kind.INT, Kind.LONG, Kind.BOOLEAN,
                Kind.CHILD }) {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> ReaderCodegen.generate(Bean.class, List.of(new GenProp("x", kind, null)),
                            null, new int[] { 0 }, null, null, Construction.instantiator(null),
                            false, false, ViewStrategy.NONE, null));
            assertTrue(e.getMessage().contains("no store handle for " + kind), e.getMessage());
        }
    }
}
