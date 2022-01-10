import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

/**
 * The following code produces the class file stored in the data arrays in CrossLoaderAccess.
 * Loading the Java compiler is extremely expensive so we inline the byte data instead.
 *
 * This code is not compiled or shipped with Blackbird, just here for maintainer reference.
 */
class GrantAccess {
    private static MethodHandles.Lookup grantAccessSlow(final MethodHandles.Lookup lookup, final Package pkg) throws IOException, ReflectiveOperationException {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        final BlackbirdDiagnosticListener<JavaFileObject> diagnostics = new BlackbirdDiagnosticListener<>();
        final StandardJavaFileManager delegateFileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8);
        final String myPackage = pkg.getName();
        final BlackbirdFileManager fileManager = new BlackbirdFileManager(delegateFileManager, myPackage);
        final JavaFileObject file = new BlackbirdFileManager.BlackbirdInputJavaFileObject("BlackbirdAccess",  "package " + myPackage + "; public class BlackbirdAccess { public static final java.lang.invoke.MethodHandles.Lookup LOOKUP = java.lang.invoke.MethodHandles.lookup(); }");
        compiler
            .getTask(null, fileManager, diagnostics, Arrays.asList("--release", "8"), null, Arrays.asList(file))
            .call();
        diagnostics.assertSuccess();
        final FileObject accessClassFile = fileManager.getFileForInput(StandardLocation.CLASS_PATH, myPackage, "BlackbirdAccess");
        final byte[] accessClassBytes = accessClassFile.openInputStream().readAllBytes();
        final Class<?> accessClass = lookup.defineClass(accessClassBytes);
        return (MethodHandles.Lookup) accessClass.getField("LOOKUP").get(null);
    }
}

class BlackbirdDiagnosticListener<S> implements DiagnosticListener<S> {
    private static final Set<Diagnostic.Kind> FAILURES;

    static {
        FAILURES = Collections.unmodifiableSet(
                EnumSet.of(Diagnostic.Kind.ERROR, Diagnostic.Kind.WARNING, Diagnostic.Kind.MANDATORY_WARNING));
    }

    private final List<Diagnostic<? extends S>> failures = new ArrayList<>();

    @Override
    public void report(final Diagnostic<? extends S> diagnostic) {
        if (FAILURES.contains(diagnostic.getKind())) {
            failures.add(diagnostic);
        }
    }

    public void assertSuccess() {
        if (!failures.isEmpty()) {
            throw new RuntimeException("Compilation failed:\n"
                    + failures.stream()
                              .map(Object::toString)
                              .collect(Collectors.joining("\n")));
        }
    }
}


class BlackbirdFileManager implements JavaFileManager {
    private final StandardJavaFileManager delegate;
    private final Map<String, BlackbirdOutputJavaFileObject> createdFiles = new HashMap<>();
    private final String myPackage;

    public BlackbirdFileManager(final StandardJavaFileManager delegate, final String myPackage) {
        this.delegate = delegate;
        this.myPackage = myPackage;
    }

    @Override
    public ClassLoader getClassLoader(final Location location) {
        return delegate.getClassLoader(location);
    }

    @Override
    public Iterable<JavaFileObject> list(final Location location, final String packageName, final Set<Kind> kinds, final boolean recurse) throws IOException {
        final Iterable<JavaFileObject> stdList = delegate.list(location, packageName, kinds, recurse);
        if (StandardLocation.CLASS_PATH.equals(location)) {
            return () -> new Iterator<JavaFileObject>() {
                boolean stdDone;
                Iterator<? extends JavaFileObject> iter;

                @Override
                public boolean hasNext() {
                    if (iter == null) {
                        iter = stdList.iterator();
                    }
                    if (iter.hasNext()) {
                        return true;
                    }
                    if (stdDone) {
                        return false;
                    }
                    stdDone = true;
                    iter = createdFiles.values().iterator();
                    return iter.hasNext();
                }

                @Override
                public JavaFileObject next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    return iter.next();
                }
            };
        }
        return stdList;
    }

    @Override
    public String inferBinaryName(final Location location, final JavaFileObject file) {
        if (file instanceof BlackbirdOutputJavaFileObject) {
            return file.getName();
        }
        return delegate.inferBinaryName(location, file);
    }

    @Override
    public boolean handleOption(final String current, final Iterator<String> remaining) {
        return delegate.handleOption(current, remaining);
    }

    @Override
    public boolean hasLocation(final Location location) {
        return delegate.hasLocation(location);
    }

    @Override
    public JavaFileObject getJavaFileForInput(final Location location, final String className, final Kind kind) throws IOException {
        return delegate.getJavaFileForInput(location, className, kind);
    }

    @Override
    public JavaFileObject getJavaFileForOutput(final Location location, final String className, final Kind kind, final FileObject sibling) throws IOException {
        final BlackbirdOutputJavaFileObject out = new BlackbirdOutputJavaFileObject(className, kind);
        createdFiles.put(className, out);
        return out;
    }

    @Override
    public FileObject getFileForInput(final Location location, final String packageName, final String relativeName) throws IOException {
        if (StandardLocation.CLASS_PATH.equals(location) && myPackage.equals(packageName)) {
            final BlackbirdOutputJavaFileObject file = createdFiles.get(packageName + "." + relativeName);
            if (file != null) {
                return file;
            }
        }
        return delegate.getFileForInput(location, packageName, relativeName);
    }

    @Override
    public FileObject getFileForOutput(final Location location, final String packageName, final String relativeName, final FileObject sibling) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void flush() throws IOException {}

    @Override
    public void close() throws IOException {}

    @Override
    public int isSupportedOption(final String option) {
        return delegate.isSupportedOption(option);
    }

    @Override
    public boolean isSameFile(final FileObject a, final FileObject b) {
        return delegate.isSameFile(a, b);
    }

    @Override
    public Location getLocationForModule(final Location location, final String moduleName) throws IOException {
        return delegate.getLocationForModule(location, moduleName);
    }

    @Override
    public Location getLocationForModule(final Location location, final JavaFileObject fo) throws IOException {
        return delegate.getLocationForModule(location, fo);
    }

    @Override
    public String inferModuleName(final Location location) throws IOException {
        return delegate.inferModuleName(location);
    }

    @Override
    public Iterable<Set<Location>> listLocationsForModules(final Location location) throws IOException {
        return delegate.listLocationsForModules(location);
    }

    @Override
    public boolean contains(final Location location, final FileObject file) throws IOException {
        return delegate.contains(location, file);
    }

    public static class BlackbirdJavaFileObject extends SimpleJavaFileObject {

        protected BlackbirdJavaFileObject(final String name, final Kind kind) {
            super(URI.create("string:///" + name.replace('.', '/')
                    + kind.extension), kind);
        }
    }

    public static class BlackbirdInputJavaFileObject extends BlackbirdJavaFileObject {
        private final String contents;

        protected BlackbirdInputJavaFileObject(final String name, final String contents) {
            super(name, Kind.SOURCE);
            this.contents = contents;
        }

        @Override
        public CharSequence getCharContent(final boolean ignoreEncodingErrors) throws IOException {
            return contents;
        }
    }

    public static class BlackbirdOutputJavaFileObject extends BlackbirdJavaFileObject {
        private final String name;
        private ByteArrayOutputStream baos = new ByteArrayOutputStream();
        private byte[] bytes;

        protected BlackbirdOutputJavaFileObject(final String name, final Kind kind) {
            super(name, kind);
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        public byte[] getBytes() {
            if (bytes == null) {
                bytes = baos.toByteArray();
                baos = null;
            }
            return bytes;
        }

        @Override
        public OutputStream openOutputStream() throws IOException {
            return baos;
        }

        @Override
        public InputStream openInputStream() throws IOException {
            return new ByteArrayInputStream(getBytes());
        }
    }
}

