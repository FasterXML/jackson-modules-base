package tools.jackson.module.blackbird.ser.otherpackage;

import com.fasterxml.jackson.annotation.JsonProperty;

// The package-private getter is invisible to subclasses in other packages, but
// Jackson finds it through the annotation and calls it through reflection
public class HiddenGetterBeanBase
{
    @JsonProperty("y")
    int getY() {
        return 3;
    }
}
