importScripts('bck2brwsr.js');

window = {};

var vm = bck2brwsr('${project.build.finalName}.jar');
vm.loadClass('org.apidesign.bck2brwsr.dew.javac.Main');

if (!window.javac) {
    throw 'No Javac defined!';
}

//onconnect = function(ev) {
//    var port = ev.ports[0];
var port = this;

port.postMessage('Ready!');
port.onmessage = function(ev) {
    var res = window.javac.compile(ev.data.html, ev.data.java);
    port.postMessage(res.toString().toString());
};
//};


