package com.fasterxml.jackson.module.androidrecord;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.fail;

public abstract class BaseTest
{
    /**
     * @param e Exception to check
     * @param anyMatches Array of Strings of which AT LEAST ONE ("any") has to be included
     *    in {@code e.getMessage()} -- using case-INSENSITIVE comparison
     */
    protected static void verifyException(Throwable e, String... anyMatches)
    {
        String msg = e.getMessage();
        String lmsg = (msg == null) ? "" : msg.toLowerCase();
        for (String match : anyMatches) {
            String lmatch = match.toLowerCase();
            if (lmsg.contains(lmatch)) {
                return;
            }
        }
        fail("Expected an exception with one of substrings ("
                +Arrays.asList(anyMatches)+"): got one (of type "+e.getClass().getName()
                +") with message \""+msg+"\"");
    }

    /*
    /**********************************************************
    /* And other helpers
    /**********************************************************
     */

    // `static` since 2.16, was only `public` before then.
    static String q(String str) {
        return '"'+str+'"';
    }
}
