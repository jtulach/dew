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

(function() {
    // init the bck2brwsr VM and compiler
    var vm = bck2brwsr('${project.build.finalName}.jar');
    vm.loadClass('org.apidesign.bck2brwsr.dew.javac.JavacEndpoint');
})(this);

function initCompiler(port) {
    if (!window.createJavac) {
        port.postMessage({ "status" : "No Javac defined!", classes : [], "errors" : [] });
        throw 'No Javac defined!';
    }
    
    var javac = window.createJavac();

    port.postMessage({ "status" : "Ready!", classes : [], "errors" : [] });
    port.onmessage = function(ev) {
        try {
            var res = javac.compile(ev.data);
            res = eval("(" + res.toString() + ")");
            port.postMessage(res);
        } catch (err) {
            port.postMessage({ "status" : "Error running compiler: " + err, classes : [], "errors" : [] });
        }
    };
}
