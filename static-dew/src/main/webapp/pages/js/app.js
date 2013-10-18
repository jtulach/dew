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
// 'use strict';

// Declare app level module which depends on filters, and services
angular.module('bck2brwsr', []).
  directive('uiCodemirror', ['$timeout', function($timeout) {
        'use strict';

        var events = ["cursorActivity", "viewportChange", "gutterClick", "focus", "blur", "scroll", "update"];
        return {
            restrict: 'A',
            require: 'ngModel',
            link: function(scope, elm, attrs, ngModel) {
                var options, opts, onChange, deferCodeMirror, codeMirror, timeoutId, val;

                if (elm[0].type !== 'textarea') {
                    throw new Error('uiCodemirror3 can only be applied to a textarea element');
                }

                options = /* uiConfig.codemirror  || */ {};
                opts = angular.extend({}, options, scope.$eval(attrs.uiCodemirror));

                onChange = function(instance, changeObj) {                    
                    val = instance.getValue();
                    $timeout.cancel(timeoutId);
                    timeoutId = $timeout(function() {
                        ngModel.$setViewValue(val);                        
                      }, 500);                    
                };
                
                deferCodeMirror = function() {
                    codeMirror = CodeMirror.fromTextArea(elm[0], opts);
                    elm[0].codeMirror = codeMirror;
                    // codeMirror.on("change", onChange(opts.onChange));
                    codeMirror.on("change", onChange);

                    for (var i = 0, n = events.length, aEvent; i < n; ++i) {
                        aEvent = opts["on" + events[i].charAt(0).toUpperCase() + events[i].slice(1)];
                        if (aEvent === void 0)
                            continue;
                        if (typeof aEvent !== "function")
                            continue;
                                                
                        var bound = _.bind( aEvent, scope );
                        
                        codeMirror.on(events[i], bound);
                    }

                    // CodeMirror expects a string, so make sure it gets one.
                    // This does not change the model.
                    ngModel.$formatters.push(function(value) {
                        if (angular.isUndefined(value) || value === null) {
                            return '';
                        }
                        else if (angular.isObject(value) || angular.isArray(value)) {
                            throw new Error('ui-codemirror cannot use an object or an array as a model');
                        }
                        return value;
                    });

                    // Override the ngModelController $render method, which is what gets called when the model is updated.
                    // This takes care of the synchronizing the codeMirror element with the underlying model, in the case that it is changed by something else.
                    ngModel.$render = function() {
                        codeMirror.setValue(ngModel.$viewValue);
                    };

                };

                $timeout(deferCodeMirror);

            }
        };
}]);

function DevCtrl( $scope, $http ) {
    var templateHtml = 
"<h1>Hello World!</h1>\n";
    var templateJava = 
"package bck2brwsr.demo;\n" +
"class YourFirstHTML5PageInRealLanguage {\n" +
"  public static void main(String... args) throws Exception { \n" +
"    throw new IllegalStateException(\"Running!\");\n" +
"  }\n" +
"}\n";

    
    $scope.makeMarker = function( editor, line ) {
        var marker = document.createElement("div");
        marker.innerHTML = " ";
        marker.className = "issue";
        
        var info = editor.lineInfo(line);
        editor.setGutterMarker(line, "issues", info.markers ? null : marker);
        
        return marker;
    };
    
    
    // Returns a function, that, as long as it continues to be invoked, will not
    // be triggered. The function will be called after it stops being called for
    // N milliseconds. If `immediate` is passed, trigger the function on the
    // leading edge, instead of the trailing.
    $scope.debounce = function(func, wait, immediate) {
      var timeout, result;
      return function() {
        var context = this, args = arguments;
        var later = function() {
          timeout = null;
          if (!immediate) result = func.apply(context, args);
        };
        var callNow = immediate && !timeout;
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
        if (callNow) result = func.apply(context, args);
        return result;
      };
    };
    
    $scope.fail = function( data ) {
        $scope.errors = eval( data );
        var editor = document.getElementById("editorJava").codeMirror;   
        editor.clearGutter( "issues" );
        
        for( var i = 0; i < $scope.errors.length; i ++ ) {
            $scope.makeMarker( editor, $scope.errors[i].line - 1 );
        }
        
    };
    
    $scope.post = function() {
        $scope.javac.postMessage({ html : $scope.html, java : $scope.java});
    };

    $scope.run = function() {
        $scope.result = $scope.html;
        
        if (!$scope.vm) {
            $scope.vm = bck2brwsr('${project.build.finalName}.jar');
        }
        var vm = $scope.vm;
        
        var first = null;
        for (var i = 0; i < $scope.classes.length; i++) {
            var cn = $scope.classes[i].className;
            cn = cn.substring(0, cn.length - 6).replace__Ljava_lang_String_2CC('/','.');
            try {
                vm.vm._reload(cn, $scope.classes[i].byteCode);
            } catch (err) {
                alert('Error loading ' + cn + ': ' + err.toString());
            }
            if (first === null) {
                first = cn;
            }
        }
        try {
            vm.loadClass(first);
        } catch (err) {
            alert('Error loading ' + first + ': ' + err.toString());
        }
    };
    
    $scope.errorClass = function( kind ) {
        switch( kind ) {
            case "ERROR" :
                return "error";
            default :         
                return "warning";   
        }
    };
    
    $scope.gotoError = function( line, col ) {
        var editor = document.getElementById("editorJava").codeMirror;   
        editor.setCursor({ line: line - 1, ch : col - 1 });
        editor.focus();
    };
    
    $scope.noClasses = function() {
        return $scope.classes === null;
    };
    
    $scope.tab = "html";
    $scope.html= templateHtml;  
    $scope.result = "";
    $scope.classes = null;
    $scope.java = templateJava;  
    var w = new Worker('compiler.js', 'javac');
    $scope.javac = w;
//    var w = new SharedWorker('compiler.js', 'javac');
//    $scope.javac = w.port;
    $scope.javac.onmessage = function(ev) {
        var obj = ev.data;
        if (obj.classes && obj.classes.length > 0) {
            $scope.classes = obj.classes;
            $scope.errors = null;
            var editor = document.getElementById("editorJava").codeMirror;   
            editor.clearGutter( "issues" );
            // initialize the VM
            var script = window.document.getElementById("bck2brwsr");
            script.src = "bck2brwsr.js";
        } else {
            $scope.classes = null;
            $scope.fail(obj.errors);
        }
        $scope.$apply("");
    };
    
    $scope.$watch( "html", $scope.debounce( $scope.post, 500 ) );
    $scope.$watch( "java", $scope.debounce( $scope.post, 500 ) );

}
