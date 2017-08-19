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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import java.util.zip.ZipFile;
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

    private Map<Location, Map<String,List<MemoryFileObject>>> generated;


    ClassLoaderFileManager() {
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
            final List<JavaFileObject> res = new ArrayList<JavaFileObject>();
            for (String resource : getResources(convertFQNToResource(packageName))) {
                final JavaFileObject jfo = new ClassLoaderJavaFileObject(resource);
                if (kinds.contains(jfo.getKind())) {
                    res.add(jfo);
                }
            }
            return res;
        } else if (canWrite(location)) {
            Map<String,List<MemoryFileObject>> folders = generated.get(location);
            List<MemoryFileObject> files = folders.get(convertFQNToResource(packageName));
            if (files != null) {
                final List<JavaFileObject> res = new ArrayList<JavaFileObject>();
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
        if (canRead(location)) {
            return new ClassLoaderJavaFileObject(convertFQNToResource(className) + kind.extension);
        } else {
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
        if (canRead(location)) {
            StringBuilder resource = new StringBuilder(convertFQNToResource(packageName));
            if (resource.length() > 0) {
                resource.append('/');   //NOI18N
            }
            resource.append(relativeName);
            return new ClassLoaderJavaFileObject(resource.toString());
        } else {
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


    private List<String> getResources(String folder) throws IOException {
        if (classPathContent == null) {
            classPathContent = new HashMap<>();
        }
        List<String> content = classPathContent.get(folder);
        if (content == null) {
            List<String> arr = new ArrayList<>();
            classPathContent.put(folder, arr);
            InputStream in = ClassLoaderFileManager.class.getResourceAsStream("pkg." + folder.replace('/', '.'));
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in));
                for (;;) {
                    String l = r.readLine();
                    if (l == null) {
                        break;
                    }
                    arr.add(l);
                }
                content = arr;
            }
        }
        return content == null ? Collections.<String>emptyList() : content;
    }
    
    public static void main(String... args) throws Exception {
        File dir = new File(args[0]);
        assert dir.isDirectory() : "Should be a directory " + dir;
        File root = new File(args[1]);
        assert root.isDirectory() : "Should be a directory " + root;
        
        Map<String,List<String>> cntent = new HashMap<>();
        
        final String cp = System.getProperty("java.class.path");
        for (String entry : cp.split(File.pathSeparator)) {
            File f = new File(entry);
            if (f.canRead()) {
                if (f.isFile()) {
                    ZipFile zf = new ZipFile(f);
                    try {
                        Enumeration<? extends ZipEntry> entries = zf.entries();
                        while (entries.hasMoreElements()) {
                            ZipEntry e = entries.nextElement();
                            if (e.isDirectory()) {
                                continue;
                            }
                            final String name = e.getName();
                            final String owner = getOwner(name);
                            List<String> content = cntent.get(owner);
                            if (content == null) {
                                content = new ArrayList<>();
                                cntent.put(owner, content);
                            }
                            content.add(name);
                            if (name.endsWith(".class")) {
                                String fqnClazz = name.replace('/', File.separatorChar).substring(0, name.length() - 6) + ".clazz";
                                File fqnFile = new File(root, fqnClazz);
                                byte[] arr = new byte[4096];
                                fqnFile.getParentFile().mkdirs();
                                try (
                                    FileOutputStream fos = new FileOutputStream(fqnFile);
                                    InputStream is = zf.getInputStream(e);
                                ) {
                                    for (;;) {
                                        int len = is.read(arr);
                                        if (len <= 0) {
                                            break;
                                        }
                                        fos.write(arr, 0, len);
                                    }
                                }
                            }
                        }
                    } finally {
                        zf.close();
                    }
                } else if (f.isDirectory()) {
                    addFiles(f, "", cntent);
                }
            }
        }
        
        for (Map.Entry<String, List<String>> en : cntent.entrySet()) {
            String pkg = en.getKey();
            List<String> classes = en.getValue();
            File f = new File(dir, "pkg." + pkg.replace('/', '.'));
            FileWriter w = new FileWriter(f);
            for (String c : classes) {
                w.append(c).append("\n");
            }
            w.close();
        }
    }

    private static void addFiles(File folder, String path, Map<String,List<String>> into) {
        String prefix = path;
        if (!prefix.isEmpty()) {
            prefix = prefix + "/";  //NOI18N
        }
        for (File f : folder.listFiles()) {
            String fname = prefix + f.getName();
            if (f.isDirectory()) {
                addFiles(f, fname, into);
            } else {
                List<String> content = into.get(path);
                if (content == null) {
                    content = new ArrayList<>();
                    into.put(path, content);
                }
                content.add(fname);
            }
        }
    }
    
    private Map<String,List<String>> classPathContent;

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
        String res = fqn.replace('.', '/'); //NOI18N
        if (res.endsWith(".class")) {
            res = res.substring(0, res.length() - 6) + ".clazz";
        }
        return res;
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

}
