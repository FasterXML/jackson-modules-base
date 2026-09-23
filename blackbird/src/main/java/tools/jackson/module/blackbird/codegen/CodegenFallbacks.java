package tools.jackson.module.blackbird.codegen;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reports codec-generation fallbacks so demotion to the stock path never hides
 * a problem silently. Gate failures are expected world diversity (exotic
 * classes make reflective gate checks throw) and log a warning once per type.
 * Generation failures are Blackbird bugs: the type passed every gate and the
 * generator still failed. They log severe once per type, and with the
 * {@code tools.jackson.module.blackbird.failOnCodegenError} system property
 * set they are rethrown instead of demoted, which the Blackbird test suites
 * enable so a masked generation bug fails the build.
 */
public final class CodegenFallbacks
{
    private static final Logger LOGGER = Logger.getLogger("tools.jackson.module.blackbird");

    // Keyed by class name, not Class, so this static set never pins a foreign
    // classloader (the leak class this module just fixed).
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    public static final String FAIL_ON_ERROR_PROPERTY =
            "tools.jackson.module.blackbird.failOnCodegenError";

    private CodegenFallbacks() {}

    public static void gateFailure(Class<?> beanClass, Throwable cause) {
        if (REPORTED.add("gate:" + beanClass.getName())) {
            LOGGER.log(Level.WARNING, "Blackbird gate check failed for {0}; the stock"
                    + " (de)serializer stays in place. Cause: {1}. Later reports for"
                    + " this type log at FINE.",
                    new Object[] { beanClass.getName(), cause });
        } else {
            LOGGER.log(Level.FINE, "Blackbird gate check failed for " + beanClass.getName(), cause);
        }
    }

    public static void generationFailure(Class<?> beanClass, Throwable cause) {
        if (Boolean.getBoolean(FAIL_ON_ERROR_PROPERTY)) {
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause instanceof Error err) {
                throw err;
            }
            throw new IllegalStateException(
                    "Blackbird codec generation failed for " + beanClass.getName(), cause);
        }
        if (REPORTED.add("gen:" + beanClass.getName())) {
            LOGGER.log(Level.SEVERE, "Blackbird codec generation failed for "
                    + beanClass.getName() + "; the stock (de)serializer stays in place."
                    + " This is likely a jackson-module-blackbird bug; please report it.",
                    cause);
        } else {
            LOGGER.log(Level.FINE,
                    "Blackbird codec generation failed for " + beanClass.getName(), cause);
        }
    }
}
