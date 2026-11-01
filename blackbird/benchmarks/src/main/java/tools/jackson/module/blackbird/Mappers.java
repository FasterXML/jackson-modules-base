package tools.jackson.module.blackbird;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.module.afterburner.AfterburnerModule;

/**
 * Creates the mapper for the {@code mode} parameter of each benchmark:
 * {@code stock} (no module), {@code blackbird} or {@code afterburner}.
 * Which Blackbird engine {@code blackbird} measures depends on the jar:
 * {@code benchmarks.jar} has this build, {@code benchmarks-classic.jar}
 * has the last release of the LambdaMetafactory engine.
 */
final class Mappers
{
    private Mappers() { }

    static ObjectMapper create(String mode) {
        return switch (mode) {
            case "stock" -> JsonMapper.builder().build();
            case "blackbird" -> JsonMapper.builder().addModule(new BlackbirdModule()).build();
            case "afterburner" -> JsonMapper.builder().addModule(new AfterburnerModule()).build();
            default -> throw new IllegalArgumentException("Unknown mode: " + mode);
        };
    }
}
