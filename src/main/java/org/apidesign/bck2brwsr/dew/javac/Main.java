package org.apidesign.bck2brwsr.dew.javac;

import java.io.IOException;
import java.util.logging.Logger;
import net.java.html.js.JavaScriptBody;

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
    
    static Object doCompile(String html, String java) throws IOException {
        Compile c = Compile.create(html, java);
        LOG.info("Compiled " + c);
        return c.toString();
    }
}
