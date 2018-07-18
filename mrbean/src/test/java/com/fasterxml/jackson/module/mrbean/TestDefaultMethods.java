package com.fasterxml.jackson.module.mrbean;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public class TestDefaultMethods
    extends BaseTest {

    public interface HasId {
        long id();
    }

    public interface HasLeguminousId extends HasId {
        long getId();
        default long id() { return getId(); }
    }

    public void testMaterializedDefaultMethod() throws IOException {
        final ObjectMapper mapper = newMrBeanMapper();

        final String input = "{\"id\": 0}";

        final HasLeguminousId bean = mapper.readValue(input, HasLeguminousId.class);

        assertEquals(bean.getId(), 0L);
        assertEquals(bean.id(), 0L); // shouldn't be implemented by mrbean

    }

}
