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
import java.util.Arrays;
import net.java.html.js.JavaScriptBody;
import net.java.html.json.Models;
import org.apidesign.bck2brwsr.vmtest.Compare;
import org.apidesign.bck2brwsr.vmtest.VMTest;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;

/**
 *
 * @author Jaroslav Tulach <jtulach@netbeans.org>
 */
public class CompileTest  {
    static {
        // make sure HTML/Java processors are loaded
        try {
            Models.toRaw(null);
        } catch (RuntimeException ex) {
            // ignore
        }
    }
    @Compare public String testCompile() throws IOException {
        String html = "";
        String java = "package x.y.z;"
            + "class X { "
            + "   static void main(String... args) { throw new RuntimeException(\"Hello brwsr!\"); }"
            + "}";
        Compile result = Compile.create(html, java);

        final byte[] bytes = result.get("x/y/z/X.class");
        assertNotNull(bytes, "Class X is compiled: " + result);
        return Arrays.toString(bytes);
    }

    @Test
    public void checkMainClassForHistogramIsFound() throws Exception {
        testMainClassForHistogram();
    }

    @Test
    public void checkMainClassForHistogramIsFound2() throws Exception {
        testMainClassForHistogram2();
    }

    @Test
    public void checkMainClassForHistogramIsFound3() throws Exception {
        testMainClassForHistogram3();
    }

    @Compare public boolean testMainClassForHistogram() throws IOException {
        String html = "";
        String java = "\n" +
"package dew.demo.histogram;\n" +
"import java.util.ArrayList;\n" +
"import java.util.List;\n" +
"import net.java.html.json.ComputedProperty;\n" +
"import net.java.html.json.Model;\n" +
"import net.java.html.json.Property;\n" +
"\n" +
"/** Model annotation generates class Data with \n" +
" * one property for list of of numbers and read-only property\n" +
" * for ten of bars.\n" +
" */\n" +
"@Model(targetId=\"\", className = \"Histogram\", properties = {\n" +
"    @Property(name = \"numbers\", type = String.class)\n" +
"})\n" +
"final class HistoModel {\n" +
"    // initialization\n" +
"    public static void main(String... args) {\n" +
"    }\n" +
"}";
        Compile result = Compile.create(html, java);

        boolean histoModelIsMain = result.isMainClass("dew/demo/histogram/HistoModel.class");
        if (!histoModelIsMain) {
            throw new IllegalStateException("HistoModel is the main class!");
        }
        return histoModelIsMain;
    }

    @Compare public boolean testMainClassForHistogram2() throws IOException {
        String html = "";
        String java = "\n" +
"package dew.demo.histogram;\n" +
"import java.util.ArrayList;\n" +
"import java.util.List;\n" +
"import net.java.html.json.ComputedProperty;\n" +
"import net.java.html.json.Model;\n" +
"import net.java.html.json.Property;\n" +
"\n" +
"     // Model annotation generates class Data with \n" +
"@Model(targetId=\"\", className = \"Histogram\", properties = {\n" +
"    @Property(name = \"numbers\", type = String.class)\n" +
"})\n" +
"final class HistoModel {\n" +
"    // initialization\n" +
"    public static void main(String... args) {\n" +
"    }\n" +
"}";
        Compile result = Compile.create(html, java);

        boolean histoModelIsMain = result.isMainClass("dew/demo/histogram/HistoModel.class");
        if (!histoModelIsMain) {
            throw new IllegalStateException("HistoModel is the main class!");
        }
        return histoModelIsMain;
    }

    @Compare public boolean testMainClassForHistogram3() throws IOException {
        String html = "";
        String java = "\n" +
"package dew.demo.histogram;\n" +
"import java.util.ArrayList;\n" +
"import java.util.List;\n" +
"import net.java.html.json.ComputedProperty;\n" +
"import net.java.html.json.Model;\n" +
"import net.java.html.json.Property;\n" +
"\n" +
"    /* Model annotation generates class Data with */ \n" +
"@Model(targetId=\"\", className = \"Histogram\", properties = {\n" +
"    @Property(name = \"numbers\", type = String.class)\n" +
"})\n" +
"final class HistoModel {\n" +
"    // initialization\n" +
"    public static void main(String... args) {\n" +
"    }\n" +
"}";
        Compile result = Compile.create(html, java);

        boolean histoModelIsMain = result.isMainClass("dew/demo/histogram/HistoModel.class");
        if (!histoModelIsMain) {
            throw new IllegalStateException("HistoModel is the main class!");
        }
        return histoModelIsMain;
    }

    @Test public void canCompilePublicClass() throws IOException {
        String html = "";
        String java = "package x.y.z;"
            + "public class X {\n"
            + "   static void main(String... args) { throw new RuntimeException(\"Hello brwsr!\"); }\n"
            + "}\n";
        Compile result = Compile.create(html, java);

        final byte[] bytes = result.get("x/y/z/X.class");
        assertNotNull(bytes, "Class X is compiled: " + result);
    }

    @Test public void mainClassIsFirst() throws IOException {
        String html = "";
        String java = "package x.y.z;"
            + "public class X {\n"
            + "   class I1 {}\n"
            + "   class I2 {}\n"
            + "   class I3 {}\n"
            + "   class I4 {}\n"
            + "   class I5 {}\n"
            + "}\n";
        JavacResult result = JavacEndpoint.newCompiler().doCompile(
            new JavacQuery(JavacEndpoint.MsgType.compile, null, html, java, 0)
        );
        assertEquals(result.getClasses().size(), 6, "Six classes generated");
        assertEquals(result.getClasses().get(0).getClassName(), "x/y/z/X.class", "Main class is the first one");
    }

    @Compare public void canGenerateCallback() throws IOException {
        Class<?> c = JavaScriptBody.class;
        assertTrue(c.isAnnotation(), "JavaScriptBody is annotation class");

        String html = "";
        String java = "package x.y.z;\n"
            + "import net.java.html.js.JavaScriptBody;\n"
            + "public class X {\n"
            + "   @JavaScriptBody(args = \"r\", javacall = true, body = \"r.@java.lang.Runnable::run()()\")\n"
            + "   public static native void call(Runnable r);"
            + "}\n";

        Compile result = Compile.create(html, java);

        final byte[] bytes = result.get("x/y/z/X.class");
        assertNotNull(bytes, "Class X is compiled: " + result);
        final byte[] bytes2 = result.get("x/y/z/$JsCallbacks$.class");
        assertNotNull(bytes2, "Class for callbacks is compiled: " + result.getErrors());
    }

    @Compare public String testAnnotationProcessorCompile() throws IOException {
        String html = "";
        String java = "package x.y.z;"
            + "@net.java.html.json.Model(className=\"Y\", properties={})\n"
            + "class X {\n"
            + "   static void main(String... args) { Y y = new Y(); }\n"
            + "}\n";
        Compile result = Compile.create(html, java);

        final byte[] bytes = result.get("x/y/z/Y.class");
        assertNotNull(bytes, "Class Y is compiled: " + result.getErrors());

        byte[] out = new byte[256];
        System.arraycopy(bytes, 0, out, 0, Math.min(out.length, bytes.length));
        return Arrays.toString(out);
    }

    @Compare public String modelReferencesClass() throws IOException {
        String html = "";
        String java = "package x.y.z;"
            + "@net.java.html.json.Model(className=\"Y\", targetId=\"\", properties={\n"
            + "  @net.java.html.json.Property(name=\"x\",type=X.class, array = true)\n"
            + "})\n"
            + "class YImpl {\n"
            + "  @net.java.html.json.Model(className=\"X\", properties={})\n"
            + "  static class XImpl {\n"
            + "  }\n"
            + "  static void main(String... args) {\n"
            + "     Y y = new Y(new X(), new X());\n"
            + "     y.applyBindings();\n"
            + "  }\n"
            + "}\n";
        Compile result = Compile.create(html, java);

        final byte[] bytes = result.get("x/y/z/Y.class");
        if (!result.getErrors().isEmpty()) {
            fail("Unexpected errors: " + result.getErrors());
        }
        assertNotNull(bytes, "Class Y is compiled: " + result);

        byte[] out = new byte[256];
        System.arraycopy(bytes, 0, out, 0, Math.min(out.length, bytes.length));
        return Arrays.toString(out);
    }

    @Factory public static Object[] create() {
        return VMTest.create(CompileTest.class);
    }

    static void assertNotNull(Object obj, String msg) {
        assert obj != null : msg;
    }

    static void assertEquals(Object real, Object exp, String msg) {
        if (real == exp) {
            return;
        }
        assert real != null && real.equals(exp) : msg + ". Expected: " + exp + " but was: " + real;
    }

    static void fail(String text) {
        throw new AssertionError(text);
    }

    static void assertTrue(boolean check, String msg) {
        if (!check) {
            fail(msg);
        }
    }
}
