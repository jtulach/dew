Development Environment for Web in Java
=======================================

This project exploits various ways of providing a development environment for Java, with its UI written in HTML.
The focus is on client only solutions that require no server to perform the compilation at all. The most functional
application of this technology can be found at

http://dew.apidesign.org/dew

where one can choose one of existing Gists, play with them, compile the changes (inside of own browser) and even execute
them thanks to [Bck2Brwsr VM](http://bck2brwsr.apidesign.org).

Current Status
==============

The code has been long time unmaintained, but recently I've got a question about it, so it runs again to some
extent. Try ...

```bash
$ mvn install -DskipTests
$ mvn -f static-dew/ bck2brwsr:show
```

... and a browser window will be opened that (in Firefox) shows the Java code editor
where one can edit the code and errors are properly detected by Javac transpiled
to JavaScript.
