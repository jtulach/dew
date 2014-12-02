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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.java.html.json.Function;
import net.java.html.json.Model;
import net.java.html.json.Property;

@Model(className = "EditorModel", properties = {
    @Property(name = "code", type = String.class),
    @Property(name = "result", type = String.class),
    @Property(name = "classpath", type = Artifact.class, array = true)
})
public class Editor {
    @Model(className = "Artifact", properties = {
        @Property(name = "groupId", type = String.class),
        @Property(name = "artifactId", type = String.class),
        @Property(name = "version", type = String.class),
        @Property(name = "classifier", type = String.class),
    })
    static final class ArtifactCntrl {
    }
    
    
    @Function static void compile(EditorModel model) {
        try {
            Compile c = Compile.create("", model.getCode());
            for (Artifact a : model.getClasspath()) {
                c.addClassPathElement(a.getGroupId(), a.getArtifactId(), a.getVersion(), a.getClassifier());
            }
            if (c.getErrors().isEmpty()) {
                model.setResult("No errors. Generated: " + c.getClasses().keySet());
            } else {
                model.setResult("Errors: " + c.getErrors());
            }
        } catch (Throwable ex) {
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            model.setResult(sw.toString());
        }
    }

    public static final void main(String... args) {
        String code = 
"package test;\n" +
"class Demo {\n" +
"  public static void main(String... args) {\n" +
"    throw new RuntimeException(\"Ahoj!\");\n" +
"  }\n" +
"}\n";
        new EditorModel(code, "",
            new Artifact("org.apidesign.bck2brwsr", "emul", "0.11", "rt")
        ).applyBindings();
    }
}
