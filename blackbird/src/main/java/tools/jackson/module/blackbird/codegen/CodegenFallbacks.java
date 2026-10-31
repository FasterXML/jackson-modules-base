package tools.jackson.module.blackbird.codegen;

import java.lang.System.Logger.Level;

/**
 * Reports codec-generation fallbacks. Both kinds leave the stock
 * (de)serializer in place, so they log at DEBUG ({@code FINE} through
 * java.util.logging) and stay silent by default. Gate failures are expected
 * world diversity: exotic classes make reflective gate checks throw.
 * Generation failures are Blackbird bugs: the type passed every gate and the
 * generator still failed. With the
 * {@code tools.jackson.module.blackbird.failOnCodegenError} system property
 * set, generation failures are rethrown instead of demoted, which the
 * Blackbird test suites enable so a masked generation bug fails the build.
 * A generation that runs out of stack is not reported here: the factories
 * demote it silently (rethrow it in strict mode), since nothing more can
 * safely run on that stack.
 */
public final class CodegenFallbacks
{
    private static final System.Logger LOGGER =
            System.getLogger("tools.jackson.module.blackbird");

    public static final String FAIL_ON_ERROR_PROPERTY =
            "tools.jackson.module.blackbird.failOnCodegenError";

    private CodegenFallbacks() {}

    public static void gateFailure(Class<?> beanClass, Throwable cause) {
        if (LOGGER.isLoggable(Level.DEBUG)) {
            LOGGER.log(Level.DEBUG, "Blackbird gate check failed for " + beanClass.getName()
                    + "; the stock (de)serializer stays in place", cause);
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
        if (LOGGER.isLoggable(Level.DEBUG)) {
            LOGGER.log(Level.DEBUG, "Blackbird codec generation failed for "
                    + beanClass.getName() + "; the stock (de)serializer stays in place."
                    + " This is likely a jackson-module-blackbird bug; please report it.", cause);
        }
    }
}
