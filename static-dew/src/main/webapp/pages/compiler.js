importScripts('bck2brwsr.js');

window = {};

var vm = bck2brwsr('${project.build.finalName}.jar');
vm.loadClass('org.apidesign.bck2brwsr.dew.javac.Main');

//onconnect = function(ev) {
//    var port = ev.ports[0];
var port = this;

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
//};


