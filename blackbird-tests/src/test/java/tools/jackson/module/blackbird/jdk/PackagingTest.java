package tools.jackson.module.blackbird.jdk;

import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.constantpool.MethodRefEntry;
import java.lang.module.ModuleDescriptor;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.deser.bean.BeanDeserializer;
import tools.jackson.module.blackbird.BlackbirdModule;
import tools.jackson.module.blackbird.codegen.MemberHandles;
import tools.jackson.module.blackbird.internal.DiscoveredBlackbirdModule;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Structural checks of the published module: what it exports (JPMS and
 * OSGi) and requires, what its helpers can reach, what it needs from
 * jackson-core, and the release guard of the build.
 */
public class PackagingTest
{
    private static Path blackbirdLocation() throws URISyntaxException {
        return Path.of(BlackbirdModule.class.getProtectionDomain().getCodeSource()
                .getLocation().toURI());
    }

    private static ModuleDescriptor descriptor() throws Exception {
        Path location = blackbirdLocation();
        if (Files.isDirectory(location)) {
            try (InputStream in = Files.newInputStream(location.resolve("module-info.class"))) {
                return ModuleDescriptor.read(in);
            }
        }
        try (JarFile jar = new JarFile(location.toFile());
                InputStream in = jar.getInputStream(jar.getEntry("module-info.class"))) {
            return ModuleDescriptor.read(in);
        }
    }

    // The internal package is not exported: generated codecs define in
    // Blackbird's own module, so no other module links to it.
    @Test
    public void moduleExportsOnlyTheModulePackage() throws Exception {
        ModuleDescriptor d = descriptor();
        assertEquals(Set.of("tools.jackson.module.blackbird"), d.exports().stream()
                .map(ModuleDescriptor.Exports::source).collect(Collectors.toSet()));
        assertTrue(d.exports().stream().noneMatch(ModuleDescriptor.Exports::isQualified));
        assertTrue(d.opens().isEmpty());
    }

    // Logging goes through System.Logger; no java.logging dependency.
    @Test
    public void moduleRequiresNoJavaLogging() throws Exception {
        Set<String> requires = descriptor().requires().stream()
                .map(ModuleDescriptor.Requires::name).collect(Collectors.toSet());
        assertFalse(requires.contains("java.logging"), requires.toString());
        assertEquals(Set.of("java.base", "tools.jackson.core", "tools.jackson.databind"),
                requires);
    }

    @Test
    public void moduleProvidesTheDiscoveryProvider() throws Exception {
        ModuleDescriptor.Provides provides = descriptor().provides().stream()
                .filter(p -> p.service().equals(JacksonModule.class.getName()))
                .findFirst().orElseThrow();
        assertEquals(java.util.List.of(DiscoveredBlackbirdModule.class.getName()),
                provides.providers());
    }

    // OSGi exports match the module descriptor.
    @Test
    public void bundleExportsOnlyTheModulePackage() throws Exception {
        Path location = blackbirdLocation();
        assumeTrue(Files.isRegularFile(location), "Needs the packaged Blackbird jar (mvn verify);"
                + " this run reads " + location);
        try (JarFile jar = new JarFile(location.toFile())) {
            String exports = jar.getManifest().getMainAttributes().getValue("Export-Package");
            assertTrue(exports.startsWith("tools.jackson.module.blackbird;"), exports);
            assertFalse(exports.contains("tools.jackson.module.blackbird."), exports);
        }
    }

    // MemberHandles grants no access of its own: a member nobody made
    // accessible unreflects only when public. Blackbird's own full-privilege
    // lookup would also reach Blackbird's package-private members.
    @Test
    public void memberHandlesGrantNoPrivilege() throws Exception {
        // A package-private static method of a package-private class in the
        // package of MemberHandles.
        Class<?> descs = Class.forName("tools.jackson.module.blackbird.codegen.Descs");
        Method internal = descs.getDeclaredMethod("of", Class.class);
        assertThrows(IllegalAccessException.class, () -> MemberHandles.unary(internal));
        Method open = descs.getDeclaredMethod("of", Class.class);
        open.setAccessible(true);
        assertNotNull(MemberHandles.unary(open));
    }

    // The generated readers call JsonParser.nextNameMatchAndToken (new
    // in jackson-core 3.3), and so does stock BeanDeserializer: Blackbird
    // needs no newer jackson-core than the databind it runs with.
    @Test
    public void stockDatabindAlreadyNeedsFusedNameMatch() throws Exception {
        assertNotNull(JsonParser.class.getMethod("nextNameMatchAndToken",
                tools.jackson.core.sym.PropertyNameMatcher.class));
        ClassModel stock;
        try (InputStream in = BeanDeserializer.class.getResourceAsStream(
                "BeanDeserializer.class")) {
            stock = ClassFile.of().parse(in.readAllBytes());
        }
        boolean calls = false;
        for (var entry : stock.constantPool()) {
            if (entry instanceof MethodRefEntry ref
                    && ref.name().equalsString("nextNameMatchAndToken")) {
                calls = true;
            }
        }
        assertTrue(calls, "stock BeanDeserializer no longer calls nextNameMatchAndToken");
    }

    // A release built on JDK < 25 would leave Blackbird out. The release profile (maven-release-plugin passes -Prelease)
    // requires JDK 25; a manual check confirmed that
    // "mvn -N -Prelease validate" fails on JDK 21 and passes on JDK 25.
    @Test
    public void releaseProfileRequiresJdk25() throws Exception {
        Path rootPom = Path.of(System.getProperty("user.dir")).resolveSibling("pom.xml");
        assumeTrue(Files.isRegularFile(rootPom), "root pom.xml not found from "
                + System.getProperty("user.dir"));
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(rootPom.toFile());
        String version = (String) XPathFactory.newInstance().newXPath().evaluate(
                "/*[local-name()='project']/*[local-name()='profiles']"
                        + "/*[local-name()='profile'][*[local-name()='id']='release']"
                        + "//*[local-name()='requireJavaVersion']/*[local-name()='version']",
                doc, XPathConstants.STRING);
        assertEquals("[25,)", version.trim());
    }
}
