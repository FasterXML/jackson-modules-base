package com.fasterxml.jackson.module.blackbird.ser;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.blackbird.BlackbirdTestBase;

import javax.tools.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestNoPackageSerialization extends BlackbirdTestBase
{
    @Test
    public void testSerializeDeserializeDefaultPackageClass() throws Exception {
        // Define the source code for a class in the default package (no package
        // declaration)
        String source = "package dynamicClassTest;" +
                "public class Person {" +
                "    public String name;" +
                "    public int age;" +
                "    public Person() {}" +
                "    public Person(String name, int age) {" +
                "        this.name = name;" +
                "        this.age = age;" +
                "    }" +
                "}";

        // Create a temporary directory for the compiled class
        Path tempDir = Files.createTempDirectory("dynamicClassTest");
        File sourceFile = new File(tempDir.toFile(), "Person.java");
        //System.out.println(sourceFile.getAbsolutePath());
        Files.write(sourceFile.toPath(), source.getBytes(StandardCharsets.UTF_8));

        // Compile the source file using the JDK compiler
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjects(sourceFile);
        JavaCompiler.CompilationTask task = compiler.getTask(
                null, fileManager, null,
                Arrays.asList("-d", tempDir.toString()),
                null, compilationUnits);
        assertTrue(task.call(), "Compilation failed");
        fileManager.close();

        // Load the compiled class using a URLClassLoader
        try (URLClassLoader classLoader = new URLClassLoader(new URL[] { tempDir.toUri().toURL() })) {
            Class<?> personClass = classLoader.loadClass("dynamicClassTest.Person");

            // Instantiate the Person object using reflection
            Object personInstance = personClass.getConstructor(String.class, int.class)
                    .newInstance("John Doe", 42);
            final ObjectMapper mapper = newObjectMapper();

            // Perform serialization and deserialization
            String json = mapper.writeValueAsString(personInstance);
            Object deserialized = mapper.readValue(json, personClass);

            // Verify that the deserialized object has the expected field values via
            // reflection
            Field nameField = personClass.getField("name");
            Field ageField = personClass.getField("age");

            assertEquals("John Doe", nameField.get(deserialized));
            assertEquals(42, ageField.get(deserialized));
        }
    }
}
