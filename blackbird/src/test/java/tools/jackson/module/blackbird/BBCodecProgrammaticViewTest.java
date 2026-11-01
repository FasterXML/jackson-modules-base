package tools.jackson.module.blackbird;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonView;

import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyName;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.deser.BeanDeserializerBuilder;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.BeanSerializerBuilder;
import tools.jackson.databind.ser.ValueSerializerModifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Views assigned in code rather than through {@code @JsonView}: a
 * deserializer modifier that sets a property's views, and a serializer
 * modifier that replaces the filtered-writer array. Stock honors both under
 * an active view, so the generated codecs must too; every case compares
 * against a vanilla mapper with the same modifier.
 */
public class BBCodecProgrammaticViewTest extends BlackbirdTestBase
{
    public static class Public { }
    public static class Admin { }

    public static class RoleBean {
        public String name;
        public String role;
    }

    public record RoleRecord(String name, String role) { }

    @JsonDeserialize(builder = RoleBuilt.Builder.class)
    public static class RoleBuilt {
        final String name, role;

        RoleBuilt(String name, String role) {
            this.name = name;
            this.role = role;
        }

        public static class Builder {
            private String name, role;

            public Builder withName(String v) { name = v; return this; }
            public Builder withRole(String v) { role = v; return this; }
            public RoleBuilt build() { return new RoleBuilt(name, role); }
        }
    }

    public static class Person {
        public String name = "Bob";
        public int age = 42;
        public String ssn = "123-45-6789";
    }

    public static class Account {
        public String name = "acct";
        @JsonView(Admin.class)
        public String secret = "s3";
    }

    private static final String ROLE_DOC = "{\"name\":\"x\",\"role\":\"admin\"}";

    // Restricts "role" to the Admin view.
    private static SimpleModule roleViews() {
        return new SimpleModule("roleViews").setDeserializerModifier(new ValueDeserializerModifier() {
            private static final long serialVersionUID = 1L;

            @Override
            public BeanDeserializerBuilder updateBuilder(DeserializationConfig config,
                    BeanDescription.Supplier beanDescRef, BeanDeserializerBuilder builder) {
                SettableBeanProperty role = builder.findProperty(PropertyName.construct("role"));
                if (role != null) {
                    role.setViews(new Class<?>[] { Admin.class });
                }
                return builder;
            }
        });
    }

    // Leaves "ssn" out of the filtered writers (Person); with default
    // inclusion off, makes "name" visible in every view (Account).
    private static SimpleModule filteredWriters() {
        return new SimpleModule("filteredWriters").setSerializerModifier(new ValueSerializerModifier() {
            private static final long serialVersionUID = 1L;

            @Override
            public BeanSerializerBuilder updateBuilder(SerializationConfig config,
                    BeanDescription.Supplier beanDesc, BeanSerializerBuilder builder) {
                Class<?> raw = beanDesc.getBeanClass();
                if (raw != Person.class && raw != Account.class) {
                    return builder;
                }
                List<BeanPropertyWriter> props = builder.getProperties();
                BeanPropertyWriter[] filtered = (builder.getFilteredProperties() == null)
                        ? props.toArray(new BeanPropertyWriter[0])
                        : builder.getFilteredProperties().clone();
                for (int i = 0; i < filtered.length; i++) {
                    String name = props.get(i).getName();
                    if (raw == Person.class && name.equals("ssn")) {
                        filtered[i] = null;
                    } else if (raw == Account.class && name.equals("name")) {
                        filtered[i] = props.get(i);
                    }
                }
                builder.setFilteredProperties(filtered);
                return builder;
            }
        });
    }

    // With default inclusion on (off by default in 3.x), unannotated
    // properties carry no views, so nothing but the views set in code can
    // hide "role".
    @Test
    public void testReaderHonorsViewsSetInUpdateBuilder() throws Exception {
        ObjectMapper vanilla = JsonMapper.builder().addModule(roleViews())
                .enable(MapperFeature.DEFAULT_VIEW_INCLUSION).build();
        ObjectMapper module = mapperBuilder().addModule(roleViews())
                .enable(MapperFeature.DEFAULT_VIEW_INCLUSION).build();

        RoleBean eb = vanilla.readerWithView(Public.class).forType(RoleBean.class).readValue(ROLE_DOC);
        RoleBean ab = module.readerWithView(Public.class).forType(RoleBean.class).readValue(ROLE_DOC);
        assertNull(eb.role);
        assertEquals(eb.name, ab.name);
        assertEquals(eb.role, ab.role);
        assertGeneratedReader(module, RoleBean.class);

        RoleRecord er = vanilla.readerWithView(Public.class).forType(RoleRecord.class).readValue(ROLE_DOC);
        RoleRecord ar = module.readerWithView(Public.class).forType(RoleRecord.class).readValue(ROLE_DOC);
        assertNull(er.role());
        assertEquals(er, ar);
        assertGeneratedReader(module, RoleRecord.class);

        RoleBuilt ec = vanilla.readerWithView(Public.class).forType(RoleBuilt.class).readValue(ROLE_DOC);
        RoleBuilt ac = module.readerWithView(Public.class).forType(RoleBuilt.class).readValue(ROLE_DOC);
        assertNull(ec.role);
        assertEquals(ec.name, ac.name);
        assertEquals(ec.role, ac.role);
        assertGeneratedReader(module, RoleBuilt.class);

        // Admin sees the property in both.
        RoleBean admin = module.readerWithView(Admin.class).forType(RoleBean.class).readValue(ROLE_DOC);
        assertEquals("admin", admin.role);
    }

    @Test
    public void testReaderFailsOnUnexpectedViewPropertySetInCode() throws Exception {
        for (Class<?> type : new Class<?>[] { RoleBean.class, RoleRecord.class, RoleBuilt.class }) {
            ObjectMapper vanilla = JsonMapper.builder().addModule(roleViews())
                    .enable(MapperFeature.DEFAULT_VIEW_INCLUSION)
                    .enable(DeserializationFeature.FAIL_ON_UNEXPECTED_VIEW_PROPERTIES).build();
            ObjectMapper module = mapperBuilder().addModule(roleViews())
                    .enable(MapperFeature.DEFAULT_VIEW_INCLUSION)
                    .enable(DeserializationFeature.FAIL_ON_UNEXPECTED_VIEW_PROPERTIES).build();
            DatabindException expected = assertThrows(DatabindException.class,
                    () -> vanilla.readerWithView(Public.class).forType(type).readValue(ROLE_DOC));
            DatabindException actual = assertThrows(DatabindException.class,
                    () -> module.readerWithView(Public.class).forType(type).readValue(ROLE_DOC),
                    type.getSimpleName());
            assertEquals(expected.getClass(), actual.getClass());
        }
    }

    // Default inclusion on: no property declares a view, so the filtered
    // array exists only because the modifier set one.
    @Test
    public void testWriterHonorsFilteredWritersFromUpdateBuilder() throws Exception {
        ObjectMapper vanilla = JsonMapper.builder().addModule(filteredWriters())
                .enable(MapperFeature.DEFAULT_VIEW_INCLUSION).build();
        ObjectMapper module = mapperBuilder().addModule(filteredWriters())
                .enable(MapperFeature.DEFAULT_VIEW_INCLUSION).build();
        String expected = vanilla.writerWithView(Public.class).writeValueAsString(new Person());
        assertFalse(expected.contains("ssn"), expected);
        assertEquals(expected, module.writerWithView(Public.class).writeValueAsString(new Person()));
        // Without a view the filtered writers do not apply.
        assertEquals(vanilla.writeValueAsString(new Person()),
                module.writeValueAsString(new Person()));
        assertGeneratedWriter(module, Person.class);
    }

    @Test
    public void testWriterHonorsWriterAddedToFilteredArray() throws Exception {
        ObjectMapper vanilla = JsonMapper.builder().addModule(filteredWriters())
                .disable(MapperFeature.DEFAULT_VIEW_INCLUSION).build();
        ObjectMapper module = mapperBuilder().addModule(filteredWriters())
                .disable(MapperFeature.DEFAULT_VIEW_INCLUSION).build();
        for (Class<?> view : new Class<?>[] { Public.class, Admin.class }) {
            String expected = vanilla.writerWithView(view).writeValueAsString(new Account());
            assertTrue(expected.contains("\"name\""), expected);
            assertEquals(expected, module.writerWithView(view).writeValueAsString(new Account()),
                    view.getSimpleName());
        }
        assertGeneratedWriter(module, Account.class);
    }
}
