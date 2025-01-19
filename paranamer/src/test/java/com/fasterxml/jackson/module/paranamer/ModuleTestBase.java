package com.fasterxml.jackson.module.paranamer;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public abstract class ModuleTestBase
{
    protected void verifyException(Throwable e, String... matches)
    {
        String msg = e.getMessage();
        String lmsg = (msg == null) ? "" : msg.toLowerCase();
        for (String match : matches) {
            String lmatch = match.toLowerCase();
            if (lmsg.indexOf(lmatch) >= 0) {
                return;
            }
        }
        fail("Expected an exception with one of substrings ("+Arrays.asList(matches)+"): got one with message \""+msg+"\"");
    }

    public String quote(String str) {
        return '"'+str+'"';
    }
}
