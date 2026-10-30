package tools.jackson.module.blackbird.deser.isolated;

// calls the inherited method, which the JVM checks against the child loader's `Token`
public class TokenBuilder extends TokenParent
{
    public TokenBuilt build() {
        withToken(null);
        return new TokenBuilt(calls);
    }
}
