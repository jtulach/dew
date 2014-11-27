package org.apidesign.bck2brwsr.dew.javac;

import net.java.html.boot.BrowserBuilder;

public final class Main {
    private Main() {
    }
    
    public static void main(String... args) throws Exception {
        BrowserBuilder.newBrowser().
            loadPage("pages/index.html").
            loadClass(Main.class).
            invoke("onPageLoad", args).
            showAndWait();
        System.exit(0);
    }

    /**
     * Called when the page is ready.
     */
    public static void onPageLoad() throws Exception {
        Data d = new Data();
        d.setMessage("Hello World from HTML and Java!");
        d.applyBindings();
    }
    
}
