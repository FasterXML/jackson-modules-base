package tools.jackson.module.jakarta.xmlbind.id;

import java.util.*;

import org.junit.jupiter.api.Test;

import jakarta.xml.bind.annotation.*;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import tools.jackson.core.type.TypeReference;

import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationIntrospector;
import tools.jackson.module.jakarta.xmlbind.ModuleTestBase;

import static org.junit.jupiter.api.Assertions.*;

public class TestXmlID2 extends ModuleTestBase
{
    @XmlRootElement(name = "department")
    @XmlAccessorType(XmlAccessType.FIELD)
    static class Department {
        @XmlElement
        @XmlID
        public Long id;

        public String name;

        @XmlIDREF
        public List<User> employees = new ArrayList<User>();

        protected Department() { }
        public Department(Long id) {
            this.id = id;
        }
        
        public void setId(Long id) {
            this.id = id;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setEmployees(List<User> employees) {
            this.employees = employees;
        }
    }

    @XmlRootElement(name = "user")
    @XmlAccessorType(XmlAccessType.FIELD)
    @JsonPropertyOrder({ "id", "username","email","department" })
    static class User
    {
        @XmlElement @XmlID
        public Long id;

        public String username;
        public String email;

        @XmlIDREF
        public Department department;

        protected User() { }
        public User(Long id) {
            this.id = id;
        }
        
        public void setId(Long id) {
            this.id = id;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public void setDepartment(Department department) {
            this.department = department;
        }
    }       
    
    private List<User> getUserList()
    {
        List<User> resultList = new ArrayList<User>();
        List<User> users = new java.util.ArrayList<User>();

        User user1, user2, user3;
        Department dep;
        user1 = new User(11L);
        user1.setUsername("11");
        user1.setEmail("11@test.com");
        user2 = new User(22L);
        user2.setUsername("22");
        user2.setEmail("22@test.com");
        user3 = new User(33L);
        user3.setUsername("33");
        user3.setEmail("33@test.com");

        dep = new Department(9L);
        dep.setName("department9");
        user1.setDepartment(dep);
        users.add(user1);
        user2.setDepartment(dep);
        users.add(user2);

        dep.setEmployees(users);
        resultList.clear();
        resultList.add(user1);
        resultList.add(user2);
        resultList.add(user3);
        return resultList;
    }
    
    @Test
    public void testIdWithJacksonRules() throws Exception
    {
        String expected = a2q("[{'id':11,'department':{'id':9,'employees':"
+"[11,{'id':22,'department':9,'email':'22@test.com','username':'22'}],"
+"'name':'department9'},'email':'11@test.com','username':'11'},22,"
+"{'id':33,'department':null,'email':'33@test.com','username':'33'}]");
        ObjectMapper mapper = JsonMapper.builder()
                // true -> ignore XmlIDREF annotation
                .annotationIntrospector(new JakartaXmlBindAnnotationIntrospector(true))
                .build();
        
        // first, with default settings (first NOT as id)
        List<User> users = getUserList();
        String json = mapper.writeValueAsString(users);
        assertEquals(expected, json);
    
        List<User> result = mapper.readValue(json, new TypeReference<List<User>>() { });
        assertEquals(3, result.size());
        assertEquals(Long.valueOf(11), result.get(0).id);
        assertEquals(Long.valueOf(22), result.get(1).id);
        assertEquals(Long.valueOf(33), result.get(2).id);
    }
    
    @Test
    public void testIdWithJaxbRules() throws Exception
    {
        ObjectMapper mapper = JsonMapper.builder()
                // but then also variant where ID is ALWAYS used for XmlID / XmlIDREF
                .annotationIntrospector(new JakartaXmlBindAnnotationIntrospector())
                .build();
        List<User> users = getUserList();
        final String json = mapper.writeValueAsString(users);
        String expected = a2q("[{'id':11,'department':9,'email':'11@test.com','username':'11'}"
                +",{'id':22,'department':9,'email':'22@test.com','username':'22'}"
                +",{'id':33,'department':null,'email':'33@test.com','username':'33'}]");
        
        assertEquals(expected, json);

        // However, there is no way to resolve those back, without some external mechanism...
    }
}
