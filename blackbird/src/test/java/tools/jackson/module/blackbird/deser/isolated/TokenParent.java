package tools.jackson.module.blackbird.deser.isolated;

// `Token` comes from the parent loader through the inherited setter, but the child
// loader would define its own copy if asked; the code using it relies on it not
public class TokenParent
{
    public int calls;

    public Object withToken(Token t) {
        calls++;
        return this;
    }

    public void setToken(Token t) {
        calls++;
    }

    public Token token() {
        return null;
    }
}
