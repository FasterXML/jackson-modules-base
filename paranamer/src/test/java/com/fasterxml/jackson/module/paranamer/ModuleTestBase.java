package com.fasterxml.jackson.module.paranamer;

import java.util.Arrays;

import com.fasterxml.jackson.databind.ObjectMapper;

import junit.framework.TestCase;

public abstract class ModuleTestBase
    extends TestCase
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

    protected ObjectMapper newObjectMapper() {
        return ObjectMapper.builder()
                .addModule(new ParanamerModule())
                .build();
    }

    public String quote(String str) {
        return '"'+str+'"';
    }
}
