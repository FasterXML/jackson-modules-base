package blackbird.jpms.test.beans;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.module.blackbird.BlackbirdModule;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs on the module path as the named module blackbird.jpms.test, which
 * requires blackbird: the arrangement a modular application has. A
 * package-private bean of this module must accelerate when the application
 * supplies its own lookup, because the generated codec - defined in this
 * module's package context - can resolve its supertype from blackbird's
 * exported internal package. Without a usable lookup the bean demotes to the
 * stock path and behavior is unchanged.
 *
 * Engagement is asserted through the blackbird.debug.codegen diagnostic
 * stream: byte-identical output makes generated and stock otherwise
 * indistinguishable from out here.
 */
public class JpmsPackagePrivateTest
{
    static {
        // Before any Blackbird class initializes: the factories cache this.
        System.setProperty("blackbird.debug.codegen", "true");
    }

    private static final String DOC = "{\"count\":7,\"name\":\"x\"}";

    @Test
    public void packagePrivateBeanAcceleratesWithModuleLookup() throws Exception {
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new BlackbirdModule() {
                    private static final long serialVersionUID = 1L;
                    @Override
                    protected Supplier<MethodHandles.Lookup> findLookupSupplier() {
                        return Beans::lookup;
                    }
                })
                .build();
        String err = captureErr(() -> {
            PkgBean bean = mapper.readValue(DOC, PkgBean.class);
            assertEquals(7, bean.getCount());
            assertEquals("x", bean.getName());
        });
        assertTrue(err.contains("BBReader_PkgBean"),
                "expected a generated codec for PkgBean; diagnostics:\n" + err);
        assertFalse(err.contains("null (gated)"),
                "PkgBean was gated instead of accelerated; diagnostics:\n" + err);
    }

    @Test
    public void defaultLookupDemotesToStock() throws Exception {
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new BlackbirdModule())
                .build();
        String err = captureErr(() -> {
            PkgBean bean = mapper.readValue(DOC, PkgBean.class);
            assertEquals(7, bean.getCount());
            assertEquals("x", bean.getName());
        });
        // Blackbird's own lookup cannot reach this module's package, so the
        // bean stays stock - correctly, and without an error.
        assertFalse(err.contains("BBReader_PkgBean"), err);
    }

    private interface Body {
        void run() throws Exception;
    }

    private static synchronized String captureErr(Body body) throws Exception {
        PrintStream original = System.err;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setErr(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            body.run();
        } finally {
            System.setErr(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
