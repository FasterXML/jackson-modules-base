package tools.jackson.module.blackbird.deser.isolated;

// its constructor calls the inherited method
public class TokenBean extends TokenParent
{
    public TokenBean() {
        token();
    }
}
