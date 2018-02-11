package com.fasterxml.jackson.module.jaxb.id;

import java.util.*;

import javax.xml.bind.annotation.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.module.jaxb.BaseJaxbTest;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;

// Reproduction of [Issue-9]
public class TestXmlID2 extends BaseJaxbTest
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
    
    public void testIdWithJacksonRules() throws Exception
    {
        String expected = "[{\"id\":11,\"username\":\"11\",\"email\":\"11@test.com\","
                +"\"department\":{\"id\":9,\"name\":\"department9\",\"employees\":["
                +"11,{\"id\":22,\"username\":\"22\",\"email\":\"22@test.com\","
                +"\"department\":9}]}},22,{\"id\":33,\"username\":\"33\",\"email\":\"33@test.com\",\"department\":null}]";
        ObjectMapper mapper = ObjectMapper.builder()
        // true -> ignore XmlIDREF annotation
                .annotationIntrospector(new JaxbAnnotationIntrospector(true))
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
    
    public void testIdWithJaxbRules() throws Exception
    {
        ObjectMapper mapper = ObjectMapper.builder()
        // but then also variant where ID is ALWAYS used for XmlID / XmlIDREF
                .annotationIntrospector(new JaxbAnnotationIntrospector())
                .build();
        List<User> users = getUserList();
        final String json = mapper.writeValueAsString(users);
        String expected = "[{\"id\":11,\"username\":\"11\",\"email\":\"11@test.com\",\"department\":9}"
                +",{\"id\":22,\"username\":\"22\",\"email\":\"22@test.com\",\"department\":9}"
                +",{\"id\":33,\"username\":\"33\",\"email\":\"33@test.com\",\"department\":null}]";
        
        assertEquals(expected, json);

        // However, there is no way to resolve those back, without some external mechanism...
    }
}
