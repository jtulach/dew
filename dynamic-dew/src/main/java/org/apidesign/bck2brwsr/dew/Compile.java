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
package org.apidesign.bck2brwsr.dew;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

/**
 *
 * @author Jaroslav Tulach <jtulach@netbeans.org>
 */
final class Compile implements DiagnosticListener<JavaFileObject> {
    private final List<Diagnostic<? extends JavaFileObject>> errors = new ArrayList<>();
    private final Map<String, byte[]> classes;
    private final String pkg;
    private final String cls;
    private final String html;

    private Compile(String html, String code) throws IOException {
        this.pkg = findPkg(code);
        this.cls = findCls(code);
        this.html = html;
        classes = compile(html, code);
    }

    /** Performs compilation of given HTML page and associated Java code
     */
    public static Compile create(String html, String code) throws IOException {
        return new Compile(html, code);
    }
    
    /** Checks for given class among compiled resources */
    public byte[] get(String res) {
        return classes.get(res);
    }
    
    /** Obtains errors created during compilation.
     */
    public List<Diagnostic<? extends JavaFileObject>> getErrors() {
        List<Diagnostic<? extends JavaFileObject>> err = new ArrayList<>();
        for (Diagnostic<? extends JavaFileObject> diagnostic : errors) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                err.add(diagnostic);
            }
        }
        return err;
    }
    
    private Map<String, byte[]> compile(final String html, final String code) throws IOException {
        final ClassLoaderFileManager clfm = new ClassLoaderFileManager();
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

        final Boolean res = ToolProvider.getSystemJavaCompiler().getTask(null, jfm, this, /*XXX:*/Arrays.asList("-source", "1.7", "-target", "1.7"), null, Arrays.asList(file)).call();
        Map<String, byte[]> result = new HashMap<>();
        for (MemoryFileObject generated : clfm.getGeneratedFiles(Kind.CLASS)) {
            result.put(generated.getName().substring(1), generated.getContent());
        }
        return result;
    }


    @Override
    public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
        errors.add(diagnostic);
    }
    private static String findPkg(String java) throws IOException {
        Pattern p = Pattern.compile("package\\p{javaWhitespace}*([\\p{Alnum}\\.]+)\\p{javaWhitespace}*;", Pattern.MULTILINE);
        Matcher m = p.matcher(java);
        if (!m.find()) {
            throw new IOException("Can't find package declaration in the java file");
        }
        String pkg = m.group(1);
        return pkg;
    }
    private static String findCls(String java) throws IOException {
        Pattern p = Pattern.compile("class\\p{javaWhitespace}*([\\p{Alnum}\\.]+)\\p{javaWhitespace}", Pattern.MULTILINE);
        Matcher m = p.matcher(java);
        if (!m.find()) {
            throw new IOException("Can't find package declaration in the java file");
        }
        String cls = m.group(1);
        return cls;
    }

    String getHtml() {
        String fqn = "'" + pkg + '.' + cls + "'";
        return html.replace("'${fqn}'", fqn);
    }
}
