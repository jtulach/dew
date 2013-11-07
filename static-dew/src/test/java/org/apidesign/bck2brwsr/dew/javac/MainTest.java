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
import java.util.List;

import org.apidesign.bck2brwsr.vmtest.Compare;
import org.apidesign.bck2brwsr.vmtest.VMTest;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;

/**
 *
 * @author Dusan Balek
 */
public class MainTest {

    @Compare
    public int testCompile() throws IOException {
        String html = "";
        String java = "package x.y.z;"
                + "class X {"
                + "   static void main(String... args) { throw new RuntimeException(\"Hello brwsr!\"); }"
                + "}";
        JavacResult result = JavacEndpoint.newCompiler().doCompile(
            new JavacQuery(JavacEndpoint.MsgType.compile, null, html, java, 0)
        );
        assertNotNull(result, "Null result");

        List<JavacClass> classes = result.getClasses();
        if (classes.size() > 0) {
            JavacClass clazz = classes.get(0);
            assertNotNull(clazz, "Null class");
            List<Byte> byteCode = clazz.getByteCode();
            return byteCode.size();
        } else {
            List<JavacError> errors = result.getErrors();
            for (JavacError err : errors) {
                assertNotNull(err, "Null err");
            }
            return errors.size();
        }
    }

    @Compare
    public int testAutocomplete() throws IOException {
        String html = "";
        String java = "package x.y.z;\n"
                + "class X {\n"
                + "    public static void main() { System.out.println(\"Hello brwsr!\"); }\n"
                + "}";
        JavacResult result = JavacEndpoint.newCompiler().doCompile(
            new JavacQuery(JavacEndpoint.MsgType.autocomplete, null, html, java, 64)
        );
        assertNotNull(result, "Null result");

        List<CompletionItem> completions = result.getCompletions();
        return completions.size();
    }

    @Factory public static Object[] create() {
        return VMTest.create(MainTest.class);
    }
    
    static void assertNotNull(Object obj, String msg) {
        assert obj != null : msg;
    }    
}
