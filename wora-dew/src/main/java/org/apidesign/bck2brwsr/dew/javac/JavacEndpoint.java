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
import net.java.html.BrwsrCtx;
import net.java.html.js.JavaScriptBody;
import net.java.html.json.Model;
import net.java.html.json.Models;
import net.java.html.json.Property;

import org.apidesign.bck2brwsr.dew.nbjava.JavaCompletionItem;

/** The end point one can use to communicate with Javac service.
 *
 * @author Jaroslav Tulach <jtulach@netbeans.org>
 */
public final class JavacEndpoint {
    private static final Logger LOG = Logger.getLogger(JavacEndpoint.class.getName());
    private Compile c = null;
    
    private JavacEndpoint() {
    }
    
    static {
        LOG.info("Registering Javac");
        registerJavacService();
        LOG.info("Javac service is available!");
    }
    
    static JavacEndpoint newCompiler() {
        return new JavacEndpoint();
    }
    

    @JavaScriptBody(args = {}, javacall = true, body = 
        "window.createJavac = function() {\n"
      + "  var compiler = @org.apidesign.bck2brwsr.dew.javac.JavacEndpoint::newCompiler()();\n"
      + "  this.compile = function(q) {\n"
      + "    return compiler.@org.apidesign.bck2brwsr.dew.javac.JavacEndpoint::doCompile(Ljava/lang/Object;)(q);\n"
      + "  };\n"
      + "  return this;\n"
      + "}\n"
    )
    private static void registerJavacService() {
    }
    
    JavacResult doCompile(Object query) throws IOException {
        JavacQuery q = Models.fromRaw(BrwsrCtx.findDefault(JavacQuery.class), JavacQuery.class, query);
        return doCompile(q);
    }
    
    public JavacResult doCompile(JavacQuery query) throws IOException {
        JavacResult res = new JavacResult();
        res.setType(query.getType());
        res.setState(query.getState());
        
        String java = query.getJava();
        String html = query.getHtml();
        int offset = query.getOffset();
        
        if (c == null || !java.equals(c.getJava())) {
            c = Compile.create(html, java);
            c.addClassPathElement("org.apidesign.bck2brwsr", "emul", "0.11", "rt");
        }
        switch (query.getType()) {
            case autocomplete:
                LOG.info("Autocomplete");
                for (JavaCompletionItem jci : c.getCompletions(offset)) {
                    res.getCompletions().add(jci.toCompletionItem());
                }
                res.setStatus("Autocomplete finished.");
                return res;
            case checkForErrors:
                for (Diagnostic<? extends JavaFileObject> d : c.getErrors()) {
                    res.getErrors().add(JavacErrorModel.create(d));
                }
                res.setStatus(res.getErrors().isEmpty() ? "OK. No errors found." : "There are errors!");
                return res;
            case compile:
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
    
    
    //
    // protocol interfaces
    //
    
    enum MsgType {
        autocomplete, checkForErrors, compile;
    }

    @Model(className = "JavacQuery", properties = {
        @Property(name = "type", type = MsgType.class),
        @Property(name = "state", type = String.class),
        @Property(name = "html", type = String.class),
        @Property(name = "java", type = String.class),
        @Property(name = "offset", type = int.class)
    })
    static final class JavacQueryModel {
    }

    @Model(className = "JavacResult", properties = {
        @Property(name = "type", type = MsgType.class),
        @Property(name = "state", type = String.class),
        @Property(name = "status", type = String.class),
        @Property(name = "errors", type = JavacError.class, array = true),
        @Property(name = "classes", type = JavacClass.class, array = true),
        @Property(name = "completions", type = CompletionItem.class, array = true)
    })
    static final class JavacResultModel {
    }

    @Model(className = "JavacError", properties = {
        @Property(name = "col", type = int.class),
        @Property(name = "line", type = int.class),
        @Property(name = "kind", type = Diagnostic.Kind.class),
        @Property(name = "msg", type = String.class)
    })
    static final class JavacErrorModel {
        static JavacError create(Diagnostic<? extends JavaFileObject> d) {
            return new JavacError(
                    (int)d.getColumnNumber(),
                    (int)d.getLineNumber(),
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
    
    @Model(className = "CompletionItem", properties = {
        @Property(name = "text", type = String.class),
        @Property(name = "displayText", type = String.class),
        @Property(name = "className", type = String.class),
    })
    static final class CompletionItemModel {
    }
    
}
