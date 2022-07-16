package tools.jackson.module.blackbird.ser;

@FunctionalInterface
public interface ToBooleanFunction {
     boolean applyAsBoolean(Object bean);
}
