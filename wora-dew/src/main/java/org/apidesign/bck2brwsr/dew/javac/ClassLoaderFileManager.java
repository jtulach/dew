/**
 * Development Environment for Web
 * Copyright (C) 2012-2013 Jaroslav Tulach <jaroslav.tulach@apidesign.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://opensource.org/licenses/GPL-2.0.
 */
package org.apidesign.bck2brwsr.dew.javac;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;

import static javax.tools.StandardLocation.*;

/**
 *
 * @author Tomas Zezula
 */
public class ClassLoaderFileManager implements JavaFileManager {

    private static final Location[] READ_LOCATIONS = {
        PLATFORM_CLASS_PATH,
        CLASS_PATH,
        SOURCE_PATH
    };

    private static final Location[] WRITE_LOCATIONS = {
        CLASS_OUTPUT,
        SOURCE_OUTPUT
    };

    private static final Location[] CLASS_LOADER_LOCATIONS = {
        ANNOTATION_PROCESSOR_PATH
    };

    private final Map<Location, Map<String,List<MemoryFileObject>>> generated;
    private final List<CP> cp;


    ClassLoaderFileManager() {
        cp = new ArrayList<>();
        generated = new HashMap<>();
        for (Location l : WRITE_LOCATIONS) {
            generated.put(l, new HashMap<String, List<MemoryFileObject>>());
        }
    }


    @Override
    public ClassLoader getClassLoader(Location location) {
        if (canClassLoad(location)) {
            return new SafeClassLoader(getClass().getClassLoader());
        } else {
            return null;
        }
    }

    @Override
    public Iterable<JavaFileObject> list(Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse) throws IOException {
        /* Correctly canRead(location) should be used. However in the dew the rsources are loaded
         * from the CLFM classloader so PLATFORM_CLASS_PATH and CLASSPATH are duplicates (javac allways
         * calls list for both PLATFORM_CLASS_PATH and CLASSPATH.
         * Also SOURCE_PATH is ignored as in dew there is no source path, just a single source file
         * and SOURCE_OUTPUT for AnnotationProcessors
         *
         */
        if (location == PLATFORM_CLASS_PATH /*canRead(location)*/) {
            final List<JavaFileObject> res = new ArrayList<>();
            for (JavaFileObject jfo : getResources(convertFQNToResource(packageName))) {
                if (kinds.contains(jfo.getKind())) {
                    res.add(jfo);
                }
            }
            return res;
        } else if (canWrite(location)) {
            Map<String,List<MemoryFileObject>> folders = generated.get(location);
            List<MemoryFileObject> files = folders.get(convertFQNToResource(packageName));
            if (files != null) {
                final List<JavaFileObject> res = new ArrayList<>();
                for (JavaFileObject file : files) {
                    if (kinds.contains(file.getKind()) && file.getLastModified() >= 0) {
                        res.add(file);
                    }
                }
                return res;
            }
        }
        return Collections.<JavaFileObject>emptyList();
    }

    @Override
    public String inferBinaryName(Location location, JavaFileObject file) {
        return ((InferableJavaFileObject)file).infer();
    }

    @Override
    public boolean isSameFile(FileObject a, FileObject b) {
        return a.toUri().equals(b.toUri());
    }

    @Override
    public boolean handleOption(String current, Iterator<String> remaining) {
        return false;
    }

    @Override
    public boolean hasLocation(Location location) {
        return
            location == CLASS_OUTPUT ||
            location == CLASS_PATH ||
            location == SOURCE_PATH ||
            location == ANNOTATION_PROCESSOR_PATH ||
            location == PLATFORM_CLASS_PATH;
    }

    @Override
    public JavaFileObject getJavaFileForInput(Location location, String className, JavaFileObject.Kind kind) throws IOException {
//        if (canRead(location)) {
//            return new ClassLoaderJavaFileObject(convertFQNToResource(className) + kind.extension);
//        } else {
        {
            throw new UnsupportedOperationException("Unsupported location for reading java file: " + location);   //NOI18N
        }
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException {
        if (canWrite(location)) {
            final String resource = convertFQNToResource(className) + kind.extension;
            final MemoryFileObject res = new MemoryFileObject(resource, null);
            register(location, resource, res);
            return res;
        } else {
            throw new UnsupportedOperationException("Unsupported location for writing java : " + location);   //NOI18N
        }
    }

    @Override
    public FileObject getFileForInput(Location location, String packageName, String relativeName) throws IOException {
//        if (canRead(location)) {
//            StringBuilder resource = new StringBuilder(convertFQNToResource(packageName));
//            if (resource.length() > 0) {
//                resource.append('/');   //NOI18N
//            }
//            resource.append(relativeName);
//            return new ClassLoaderJavaFileObject(resource.toString());
//        } else {
        {
            throw new UnsupportedOperationException("Unsupported location for reading file: " + location);   //NOI18N
        }
    }

    @Override
    public FileObject getFileForOutput(Location location, String packageName, String relativeName, FileObject sibling) throws IOException {
        if (canWrite(location)) {
            StringBuilder resource = new StringBuilder(convertFQNToResource(packageName));
            if (resource.length() > 0) {
                resource.append('/');   //NOI18N
            }
            resource.append(relativeName);
            String resourceStr = resource.toString();
            final MemoryFileObject res = new MemoryFileObject(resourceStr, null);
            register(location, resourceStr, res);
            return res;
        } else {
            throw new UnsupportedOperationException("Unsupported location for writing file: " + location);   //NOI18N
        }
    }

    @Override
    public void flush() throws IOException {
    }

    @Override
    public void close() throws IOException {        
    }

    @Override
    public int isSupportedOption(String option) {
        return -1;
    }


    private List<JavaFileObject> getResources(String folder) throws IOException {
        if (classPathContent == null) {
            classPathContent = new HashMap<>();
        }
        List<JavaFileObject> content = classPathContent.get(folder);
        if (content == null) {
            List<JavaFileObject> arr = new ArrayList<>();
            for (CP e : this.cp) {
                e.listResources(folder, arr);
            }
            content = arr;
            classPathContent.put(folder, arr);
        }
        return content;
    }
    private Map<String,List<JavaFileObject>> classPathContent;

    private void register(Location loc, String resource, MemoryFileObject jfo) {
        Map<String,List<MemoryFileObject>> folders = generated.get(loc);
        final String folder = getOwner(resource);
        List<MemoryFileObject> content = folders.get(folder);
        if (content == null) {
            content = new ArrayList<>();
            folders.put(folder, content);
        }
        content.add(jfo);
    }

    private static String getOwner(String resource) {
        int lastSlash = resource.lastIndexOf('/');  //NOI18N
        assert lastSlash != 0;
        return lastSlash < 0 ?
            resource :
            resource.substring(0, lastSlash);
    }

    private static boolean canRead(Location loc) {
        for (Location rl : READ_LOCATIONS) {
            if (rl.equals(loc)) {
                return true;
            }
        }
        return false;
    }

    private static boolean canWrite(Location loc) {
        for (Location wl : WRITE_LOCATIONS) {
            if (wl.equals(loc)) {
                return true;
            }
        }
        return false;
    }

    private static boolean canClassLoad(Location loc) {
        for (Location cll : CLASS_LOADER_LOCATIONS) {
            if (cll.equals(loc)) {
                return true;
            }
        }
        return false;
    }

    static String convertFQNToResource(String fqn) {
        return fqn.replace('.', '/');   //NOI18N
    }

    static String convertResourceToFQN(String resource) {
        assert !resource.startsWith("/");    //NOI18N
        int lastSlash = resource.lastIndexOf('/');  //NOI18N
        int lastDot = resource.lastIndexOf('.');    //NOI18N
        if (lastSlash < lastDot) {
            resource = resource.substring(0, lastDot);
        }
        return resource.replace('/', '.');    //NOI18N
    }


    JavaFileObject createMemoryFileObject (String resourceName, JavaFileObject.Kind kind, byte[] content) {
        final InferableJavaFileObject jfo  = new MemoryFileObject(resourceName, kind, content);
        return jfo;
    }

    Iterable<? extends MemoryFileObject> getGeneratedFiles(JavaFileObject.Kind... kinds) {
        final Set<JavaFileObject.Kind> ks = EnumSet.noneOf(JavaFileObject.Kind.class);
        Collections.addAll(ks, kinds);
        final List<MemoryFileObject> res = new ArrayList<>();
        for (Map<String,List<MemoryFileObject>> folders : generated.values()) {
            for (List<MemoryFileObject> content : folders.values()) {
                for (MemoryFileObject fo : content) {
                    if (ks.contains(fo.getKind()) && fo.getLastModified() >= 0) {
                        res.add(fo);
                    }
                }
            }
        }
        return res;
    }

    final void addCp(CP element) {
        cp.add(element);
    }

    private static final class SafeClassLoader extends ClassLoader {
        private final ClassLoader delegate;

        SafeClassLoader(final ClassLoader delegate) {
            this.delegate = delegate;

        }

        @Override
        public URL getResource(String name) {
            return delegate.getResource(name);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            return delegate.getResourceAsStream(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            return delegate.getResources(name);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            return delegate.loadClass(name);
        }
    }

    static class CP {
        final String artifactId;
        final String groupId;
        final String version;
        final String spec;

        public CP(String groupId, String artifactId, String version, String spec) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.spec = spec;
        }

        final void listResources(String folder, List<JavaFileObject> arr) throws IOException {
            URL m2 = new URL("file:///home/jarda/.m2/repository/");
            
            String relative = groupId.replace('.', '/') + "/" +
                artifactId + "/" + version + "/" + 
                artifactId + "-" + version;
            if (spec != null) {
                relative += "-" + spec;
            }
            relative += ".jar";
            
            URL artifact = new URL(m2, relative);
            InputStream is = artifact.openStream();
            
            ZipInputStream zis = new ZipInputStream(is);
            for (;;) {
                ZipEntry ze = zis.getNextEntry();
                if (ze == null) {
                    break;
                }
                if (ze.getName().startsWith(folder)) {
                    String rest = ze.getName().substring(folder.length());
                    if (rest.startsWith("/")) {
                        rest = rest.substring(1);
                    }
                    if (rest.isEmpty() || rest.indexOf('/') >= 0) {
                        continue;
                    }
                    byte[] data = new byte[(int)ze.getSize()];
                    int offset = 0;
                    while (offset < data.length) {
                        int read = zis.read(data, offset, data.length - offset);
                        if (read == -1) {
                            break;
                        }
                        offset += read;
                    }
                    arr.add(new ClassLoaderJavaFileObject(rest, data));
                }
            }
            zis.close();
        }
    }

}
