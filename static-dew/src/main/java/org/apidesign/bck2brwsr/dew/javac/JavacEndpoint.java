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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import net.java.html.js.JavaScriptBody;
import net.java.html.json.Model;
import net.java.html.json.Property;

/**
 *
 * @author Jaroslav Tulach <jtulach@netbeans.org>
 */
public final class Main {
    private static final Logger LOG = Logger.getLogger(Main.class.getName());
    private static Compile c = null;
    
    static {
        LOG.info("Registering Javac");
        registerJavacService();
        LOG.info("Javac service is available!");
    }
    

    @JavaScriptBody(args = {}, javacall = true, body = 
        "window.javac = {};\n"
      + "window.javac.compile = function(type,html,java,offset) {\n"
      + "  return @org.apidesign.bck2brwsr.dew.javac.Main::doCompile(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;I)(type,html,java,offset);\n"
      + "}\n"
    )
    private static void registerJavacService() {
    }
    
    static JavacResult doCompile(String type, String html, String java, int offset) throws IOException {
        JavacResult res = new JavacResult();
        res.setType(type);
        if (c == null || !java.equals(c.getJava())) {
            c = Compile.create(html, java);
        }
        switch (type) {
            case "autocomplete":
                LOG.info("Autocomplete");
                res.getCompletions().addAll(c.getCompletions(offset));
                res.setStatus("Autocomplete finished.");
                return res;
            case "checkForErrors":
                for (Diagnostic<? extends JavaFileObject> d : c.getErrors()) {
                    res.getErrors().add(JavacErrorModel.create(d));
                }
                res.setStatus(res.getErrors().isEmpty() ? "OK. No errors found." : "There are errors!");
                return res;
            case "compile":
                LOG.log(Level.INFO, "Compiled {0}", c);
                for (Map.Entry<String, byte[]> e : c.getClasses().entrySet()) {
                    List<Byte> arr = new ArrayList<>(e.getValue().length);
                    for (byte b : e.getValue()) {
                        arr.add(b);
                    }
                    final JavacClass jc = new JavacClass(e.getKey());
                    jc.getByteCode().addAll(arr);
                    if (c.isMainClass(e.getKey())) {
                        res.getClasses().add(0, jc);
                    } else {
                        res.getClasses().add(jc);
                    }
                }
                res.setStatus(res.getClasses().isEmpty() ? "No bytecode has been generated!" : "OK");
                return res;
        }
        res.setStatus("Nothing to do!");
        return res;
    }
    
    @Model(className = "JavacResult", properties = {
        @Property(name = "type", type = String.class),
        @Property(name = "status", type = String.class),
        @Property(name = "errors", type = JavacError.class, array = true),
        @Property(name = "classes", type = JavacClass.class, array = true),
        @Property(name = "completions", type = String.class, array = true)
    })
    static final class JavacResultModel {
    }
    
    @Model(className = "JavacError", properties = {
        @Property(name = "col", type= long.class),
        @Property(name = "line", type = long.class),
        @Property(name = "kind", type = Diagnostic.Kind.class),
        @Property(name = "msg", type = String.class)
    })
    static final class JavacErrorModel {
        static JavacError create(Diagnostic<? extends JavaFileObject> d) {
            return new JavacError(
                d.getColumnNumber(),
                d.getLineNumber(),
                d.getKind(),
                d.getMessage(Locale.ENGLISH)
            );
        }
    }
    
    @Model(className = "JavacClass", properties = {
        @Property(name = "className", type = String.class),
        @Property(name = "byteCode", type = byte.class, array = true)
    })
    static final class JavacClassModel {
    }
}
