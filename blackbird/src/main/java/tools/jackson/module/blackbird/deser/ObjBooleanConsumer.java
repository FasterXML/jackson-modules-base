package tools.jackson.module.blackbird.deser;

@FunctionalInterface
public interface ObjBooleanConsumer {
    void accept(Object bean, boolean value);
}
