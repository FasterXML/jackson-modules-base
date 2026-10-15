package tools.jackson.module.blackbird.internal;

import tools.jackson.module.blackbird.BlackbirdModule;
import tools.jackson.module.blackbird.codegen.ClassFileEmitters;

/**
 * The service provider that module discovery instantiates
 * ({@code MapperBuilder.findAndAddModules()}, or any other reader of
 * {@code META-INF/services/tools.jackson.databind.JacksonModule}). On a
 * runtime where Blackbird cannot generate codecs (Java 17 to 24), this
 * instance registers nothing and logs one warning, so discovery keeps working
 * for every other module; an explicitly registered {@link BlackbirdModule}
 * fails instead. Not API: register {@code new BlackbirdModule()}.
 */
public final class DiscoveredBlackbirdModule extends BlackbirdModule
{
    private static final long serialVersionUID = 1L;

    // Databind keeps one module per registration id, the one added last.
    // Where Blackbird works, the id is that of an explicit registration, so
    // discovery plus an explicit registration installs Blackbird once. Where
    // it cannot work, the id is distinct, so that discovery cannot replace an
    // explicit registration, which must fail.
    @Override
    public Object getRegistrationId() {
        return ClassFileEmitters.available()
                ? BlackbirdModule.class.getName()
                : DiscoveredBlackbirdModule.class.getName();
    }

    @Override
    public String getModuleName() {
        return BlackbirdModule.class.getSimpleName();
    }
}
