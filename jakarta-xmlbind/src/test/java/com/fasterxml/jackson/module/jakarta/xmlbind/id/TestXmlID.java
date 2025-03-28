package com.fasterxml.jackson.module.jakarta.xmlbind.id;

import java.util.*;

import jakarta.xml.bind.annotation.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.module.jakarta.xmlbind.ModuleTestBase;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple testing to verify that XmlID and XMLIDREF handling works
 * to degree we can make it work.
 */
public class TestXmlID extends ModuleTestBase
{
    // From sample used in [http://blog.bdoughan.com/2010/10/jaxb-and-shared-references-xmlid-and.html]
    @XmlRootElement
    @XmlAccessorType(XmlAccessType.FIELD)
    static class Company {
     
        @XmlElement(name="employee")
        protected List<Employee> employees;
     
        public Company() {
            employees = new ArrayList<Employee>();
        }
     
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    static class Employee {
     
        @XmlAttribute
        @XmlID
        protected String id;
     
        @XmlAttribute
        protected String name;
     
        @XmlIDREF
        protected Employee manager;
     
        @XmlElement(name="report")
        @XmlIDREF
        protected List<Employee> reports;
     
        public Employee() {
            reports = new ArrayList<Employee>();
        }
    }    
    
    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    @Test
    public void testSimpleRefs() throws Exception
    {
        final ObjectMapper mapper = getJaxbMapper();
        Company company = new Company();
        
        Employee employee1 = new Employee();
        employee1.id = "1";
        employee1.name = "Jane Doe";
        company.employees.add(employee1);
 
        Employee employee2 = new Employee();
        employee2.id = "2";
        employee2.name = "John Smith";
        employee2.manager = employee1;
        employee1.reports.add(employee2);
        company.employees.add(employee2);
 
        Employee employee3 = new Employee();
        employee3.id = "3";
        employee3.name = "Anne Jones";
        employee3.manager = employee1;
        employee1.reports.add(employee3);
        company.employees.add(employee3);

        String json = mapper.writeValueAsString(company);
        // this is the easy part actually...
        assertNotNull(json);

        // then try bringing back
        Company result = mapper.readValue(json, Company.class);
        assertNotNull(result);

        assertEquals(3, company.employees.size());
        assertEquals("Jane Doe", company.employees.get(0).name);
        assertEquals("1", company.employees.get(0).id);
        assertEquals("John Smith", company.employees.get(1).name);
        assertEquals("2", company.employees.get(1).id);
        assertEquals("Anne Jones", company.employees.get(2).name);
        assertEquals("3", company.employees.get(2).id);

        // then actual references:
        final Employee resEmpl1 = company.employees.get(0);
        final Employee resEmpl2 = company.employees.get(1);
        final Employee resEmpl3 = company.employees.get(2);
        assertEquals(2, resEmpl1.reports.size());
        // Jane has John and Anne as reports:
        assertSame(resEmpl2, resEmpl1.reports.get(0));
        assertSame(resEmpl3, resEmpl1.reports.get(1));
        assertEquals(0, resEmpl2.reports.size());
        assertEquals(0, resEmpl3.reports.size());
        // and they have her as manager
        assertSame(resEmpl1, resEmpl2.manager);
        assertSame(resEmpl1, resEmpl3.manager);
    }
}
