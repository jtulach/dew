/*
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
importScripts('bck2brwsr.js');

window = {};

var vm = bck2brwsr('${project.build.finalName}.jar');
vm.loadClass('org.apidesign.bck2brwsr.dew.javac.Main');

onconnect = function(ev) {
    var port = ev.ports[0];
    //var port = this;

    if (!window.javac) {
        port.postMessage({ "status" : "No Javac defined!", classes : [], "errors" : [] });
        throw 'No Javac defined!';
    }

    port.postMessage({ "status" : "Ready!", classes : [], "errors" : [] });
    port.onmessage = function(ev) {
        var res = window.javac.compile(ev.data.html, ev.data.java);
        res = eval("(" + res.toString() + ")");
        port.postMessage(res);
    };
};


