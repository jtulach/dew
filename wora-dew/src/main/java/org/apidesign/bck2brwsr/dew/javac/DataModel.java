package org.apidesign.bck2brwsr.dew.javac;

import net.java.html.json.ComputedProperty;
import net.java.html.json.Function;
import net.java.html.json.Model;
import net.java.html.json.Property;

/** Model annotation generates class Data with 
 * one message property, boolean property and read only words property
 */
@Model(className = "Data", properties = {
    @Property(name = "message", type = String.class),
    @Property(name = "rotating", type = boolean.class)
})
final class DataModel {
    @ComputedProperty static java.util.List<String> words(String message) {
        String[] arr = new String[6];
        String[] words = message == null ? new String[0] : message.split(" ", 6);
        for (int i = 0; i < 6; i++) {
            arr[i] = words.length > i ? words[i] : "!";
        }
        return java.util.Arrays.asList(arr);
    }
    
    @Function static void turnAnimationOn(Data model) {
        model.setRotating(true);
    }
    
    @Function static void turnAnimationOff(final Data model) {
        confirmByUser("Really turn off?", new Runnable() {
            @Override
            public void run() {
                model.setRotating(false);
            }
        });
    }
    
    @Function static void rotate5s(final Data model) {
        model.setRotating(true);
        java.util.Timer timer = new java.util.Timer("Rotates a while");
        timer.schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                model.setRotating(false);
            }
        }, 5000);
    }
    
    @Function static void showScreenSize(Data model) {
        model.setMessage(screenSize());
    }
    
    /** Shows direct interaction with JavaScript */
    @net.java.html.js.JavaScriptBody(
        args = { "msg", "callback" }, 
        javacall = true, 
        body = "if (confirm(msg)) {\n"
             + "  callback.@java.lang.Runnable::run()();\n"
             + "}\n"
    )
    static native void confirmByUser(String msg, Runnable callback);
    @net.java.html.js.JavaScriptBody(
        args = {}, body = 
        "var w = window,\n" +
        "    d = document,\n" +
        "    e = d.documentElement,\n" +
        "    g = d.getElementsByTagName('body')[0],\n" +
        "    x = w.innerWidth || e.clientWidth || g.clientWidth,\n" +
        "    y = w.innerHeight|| e.clientHeight|| g.clientHeight;\n" +
        "\n" +
        "return 'Screen size is ' + x + ' times ' + y;\n"
    )
    static native String screenSize();
}
