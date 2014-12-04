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

import org.apidesign.bck2brwsr.dew.nbjava.CompilationInfo;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.StandardLocation;

import org.apidesign.bck2brwsr.dew.nbjava.JavaCompletionItem;
import org.apidesign.bck2brwsr.dew.nbjava.JavaCompletionQuery;

/**
 *
 * @author Jaroslav Tulach <jtulach@netbeans.org>
 */
final class Compile {

    private final String pkg;
    private final String cls;
    private final String html;
    private final ClassLoaderFileManager clfm;
    private final CompilationInfo info;
    private Map<String, byte[]> classes = null;
    private List<Diagnostic<? extends JavaFileObject>> errors;
    
    private Compile(String html, String code) throws IOException {
        this.pkg = find("package", ';', code);
        this.cls = find("class ", ' ', code);
        this.html = html;        
        this.clfm = new ClassLoaderFileManager();
        
        final JavaFileObject file = clfm.createMemoryFileObject(                
                ClassLoaderFileManager.convertFQNToResource(pkg.isEmpty() ? cls : pkg + "." + cls) + Kind.SOURCE.extension,
                Kind.SOURCE,
                code.getBytes());
        final JavaFileObject htmlFile = clfm.createMemoryFileObject(
            ClassLoaderFileManager.convertFQNToResource(pkg),
            Kind.OTHER,
            html.getBytes());

        JavaFileManager jfm = new ForwardingJavaFileManager<JavaFileManager>(clfm) {            
            @Override
            public FileObject getFileForInput(Location location, String packageName, String relativeName) throws IOException {
                if (location == StandardLocation.SOURCE_PATH) {
                    if (packageName.equals(pkg)) {
                        return htmlFile;
                    }
                }                
                return null;
            }
        };

        this.info = new CompilationInfo(file, jfm);
    }

    /** Performs compilation of given HTML page and associated Java code
     */
    public static Compile create(String html, String code) throws IOException {
        return new Compile(html, code);
    }
    
    /** Adds a Maven artifact in the class path
    */
    public final void addClassPathElement(
        String groupId, String artifactId, String version, String classifier
    ) {
        clfm.addCp(new ClassLoaderFileManager.CPEntry(groupId, artifactId, version, classifier));
    }

    public List<? extends JavaCompletionItem> getCompletions(int offset) {
        try {
            return JavaCompletionQuery.query(info, JavaCompletionQuery.COMPLETION_QUERY_TYPE, offset);
        } catch (Exception e) {}
        return Collections.emptyList();
    }

    /** Checks for given class among compiled resources */
    public byte[] get(String res) {
        return getClasses().get(res);
    }

    public Map<String, byte[]> getClasses() {
        if (classes == null) {
            classes = new HashMap<>();
            try {
                info.toPhase(CompilationInfo.Phase.GENERATED);
            } catch (IOException ioe) {}
            for (MemoryFileObject generated : clfm.getGeneratedFiles(Kind.CLASS)) {
                classes.put(generated.getName(), generated.getContent());
            }
        }
        return classes;
    }

    public boolean isMainClass(String name) {
        return name.endsWith('/' + cls + ".class");
    }

    /** Obtains errors created during compilation.
     */
    public List<Diagnostic<? extends JavaFileObject>> getErrors() {
        if (errors == null) {
            errors = new ArrayList<>();
            try {
                info.toPhase(CompilationInfo.Phase.RESOLVED);
            } catch (IOException ioe) {}
            for (Diagnostic<? extends JavaFileObject> diagnostic : info.getDiagnostics()) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                    errors.add(diagnostic);
                }
            }
        }
        return errors;
    }

    private static String find(String pref, char term, String java) throws IOException {
        int pkg = java.indexOf(pref);
        if (pkg != -1) {
            pkg += pref.length();
            while (Character.isWhitespace(java.charAt(pkg))) {
                pkg++;
            }
            int semicolon = java.indexOf(term, pkg);
            if (semicolon != -1) {
                String pkgName = java.substring(pkg, semicolon).trim();
                return pkgName;
            }
        }
        throw new IOException("Can't find " + pref + " declaration in the java file");
    }

    String getHtml() {
        String fqn = "'" + pkg + '.' + cls + "'";
        return html.replace("'${fqn}'", fqn);
    }

    String getJava() {
        return info != null ? info.getText() : null;
    }
}
