package com.fasterxml.jackson.module.mrbean;

import com.fasterxml.jackson.databind.*;

import java.io.IOException;

// test for [modules-base#52]
public class TestDefaultMethods
    extends BaseTest {

    public interface HasId {
        long id();
    }

    public interface HasIdImpl extends HasId {
        @Override
        default long id() {
            return 42L;
        }
    }

    public void testMaterializedDefaultMethod() throws IOException {
        final ObjectMapper mapper = newMrBeanMapper();

        // Main thing is that we should not get an exception for missing method,
        // as it is correctly deduced to have been implemented
        final HasIdImpl bean = mapper.readValue("{}", HasIdImpl.class);
        assertEquals(42L, bean.id());
    }
}
