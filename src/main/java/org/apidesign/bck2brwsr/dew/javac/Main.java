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
    
    static {
        LOG.info("Registering Javac");
        registerJavacService();
        LOG.info("Javac service is available!");
    }
    

    @JavaScriptBody(args = {}, javacall = true, body = 
        "window.javac = {};\n"
      + "window.javac.compile = function(html,java) {\n"
      + "  return @org.apidesign.bck2brwsr.dew.javac.Main::doCompile(Ljava/lang/String;Ljava/lang/String;)(html,java);\n"
      + "}\n"
    )
    private static void registerJavacService() {
    }
    
    static JavacResult doCompile(String html, String java) throws IOException {
        Compile c = Compile.create(html, java);
        LOG.log(Level.INFO, "Compiled {0}", c);
        JavacResult res = new JavacResult();
        for (Diagnostic<? extends JavaFileObject> d : c.getErrors()) {
            res.getErrors().add(JavacErrorModel.create(d));
        }
        for (Map.Entry<String, byte[]> e : c.getClasses().entrySet()) {
            List<Byte> arr = new ArrayList<>(e.getValue().length);
            for (byte b : e.getValue()) {
                arr.add(b);
            }
            final JavacClass jc = new JavacClass(e.getKey());
            jc.getByteCode().addAll(arr);
            res.getClasses().add(jc);
            
        }
        if (!res.getErrors().isEmpty()) {
            res.setStatus("There are errors!");
        } else if (res.getClasses().isEmpty()) {
            res.setStatus("No bytecode has been generated");
        } else {
            res.setStatus("OK.");
        }
        return res;
    }
    
    @Model(className = "JavacResult", properties = {
        @Property(name = "status", type = String.class),
        @Property(name = "errors", type = JavacError.class, array = true),
        @Property(name = "classes", type = JavacClass.class, array = true)
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
