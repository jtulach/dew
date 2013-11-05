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

function DevCtrl( $scope, $timeout, $http ) {
    var templateHtml = 
"<h1>Please select a sample...</h1>\n";
    var templateJava = 
"package waiting4javac;\n" +
"class ToInitialize {\n" +
"  /*int*/ myFirstError;\n" +
"}\n";

    function parseJson(s) {
      if (typeof s === 'string') {
        return JSON.parse(s);
      } else {
        return s;
      }
    }

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
        var context = this;
        var later = function() {
          timeout = null;
          if (!immediate) result = func.apply(context);
        };
        var callNow = immediate && !timeout;
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
        if (callNow) result = func.apply(context);
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
    
    $scope.run = function() {
        if ($scope.classes === null) {
            $scope.post('compile');
        }
        if (!$scope.vm) {
            // initialize the VM
            var script = window.document.getElementById("brwsrvm");
            script.src = "bck2brwsr.js";
            if (!window.bck2brwsr) {
                $scope.result('<h3>Initializing the Virtual Machine</h3> Please wait...');
                $timeout($scope.run, 100);
                return;
            }
            
            $scope.vm = window.bck2brwsr('${project.build.finalName}.jar', function(resource) {
                if ($scope.classes) {
                    for (var i = 0; i < $scope.classes.length; i++) {
                        var c = $scope.classes[i];
                        if (c.className == resource) {
                            return c.byteCode;
                        }
                    }
                }
                return null;
            });
        }
        var vm = $scope.vm;
        
        $scope.result("");
        $timeout(function() {
            $scope.result($scope.html);
        }, 100).then(function() {
            var first = null;
            for (var i = 0; i < $scope.classes.length; i++) {
                var cn = $scope.classes[i].className;
                cn = cn.substring(0, cn.length - 6).replace__Ljava_lang_String_2CC('/','.');
                try {
                    vm.vm._reload(cn, $scope.classes[i].byteCode);
                } catch (err) {
                    $scope.status = 'Error loading ' + cn + ': ' + err.toString();
                    break;
                }
                if (first === null) {
                    first = cn;
                }
            }   
            try {
                if (first !== null) {
                    vm.loadClass(first);
                    $scope.status = 'Class ' + first + ' loaded OK.';
                }
            } catch (err) {
                $scope.status = 'Error loading ' + first + ': ' + err.toString();
            }
        }, 100);
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
    
    $scope.someErrors = function() {
        return $scope.errors !== null;
    };
    
    $scope.noModification = function() {
        if (!$scope.gistid) return true;
        if (!$scope.origJava) return true;
        if (!$scope.origHtml) return true;
        if ($scope.origJava === $scope.java && $scope.origHtml === $scope.html) {
            return true;
        }
        return false;
    };

    $scope.save = function() {
        localStorage.html = $scope.html;
        localStorage.java = $scope.java;
        localStorage.gistid = $scope.gistid;
        window.open("https://github.com/login/oauth/authorize?client_id=13479cb2e9dd5f762848&scope=gist&redirect_uri=http://dew.apidesign.org/dew/save.html&state=" + $scope.gistid);
    };
    
    $scope.loadGist = function() {
        window.location.hash = "#" + $scope.gistid;
        $scope.html = "<h1>Loading gist..." + $scope.gistid + "</h1>";
        $scope.java = "package waiting4gist;\nclass ToLoad {\n  /* please wait ... */\n}\n";
        var xhr = new XMLHttpRequest();
        xhr.open("GET", "https://api.github.com/gists/" + $scope.gistid);
/*        xhr.responseType = "json"; */
        xhr.send();
        xhr.onreadystatechange = function(ev) {
            if (xhr.readyState !== 4) {
                return;
            }
            if (xhr.status !== 200) {
                $scope.description = "Can't get list of gists: " + xhr.statusText;
                $scope.description = 'Bad thing happened: ' + res.message;
            } else {
                var res = parseJson(xhr.response);
                $scope.gistid = res.id;
                $scope.userid = res.user.login;
                $scope.url = res.html_url;
                $scope.description = res.description;
                for (var f in res.files) {
                    if (f.search(/\.html$/g) >= 0) {
                        $scope.origHtml = $scope.html = res.files[f].content;
                    }
                    if (f.search(/\.java$/g) >= 0) {
                        $scope.origJava = $scope.java = res.files[f].content;
                    }
                }
                $scope.classes = null;
                $scope.errors = null;
                var editor = document.getElementById("editorJava").codeMirror;
                editor.clearGutter("issues");
            }
        };
    }

    $scope.result = function(html) {
        var e = window.document.getElementById("result");
        e.innerHTML = html;
    };

    
    $scope.url = "http://dew.apidesign.org";
    $scope.description = "Development Environment for Web";
    
    var gist = window.location.hash;
    if (!gist) {
        if (localStorage.gistid) {
            $scope.gistid = localStorage.gistid;
            $scope.java = localStorage.java;
            $scope.html = localStorage.html;
        } else {
            $scope.gistid = "-1";
        }
        $scope.samples = [{
            "description" : "Loading samples...",
            "id" : "-1"
        }];
        var samples = [
            {
               "description" : "Choose a sample...",
               "id" : ""
            }
        ];
        var loadSamples = function(res) {
            for (var i = 0; i < res.length; i++) {
                samples.push(res[i]);
            }
            $scope.samples = samples;
            $scope.gistid = "";
        };
        var xhr = new XMLHttpRequest();
        xhr.open("GET", "https://api.github.com/users/jtulach/gists");
/*        xhr.responseType = "json"; */
        xhr.send();
        xhr.onreadystatechange = function(ev) {
            if (xhr.readyState !== 4) {
                return;
            }
            if (xhr.status !== 200) {
                $scope.description = "Can't get list of gists: " + xhr.statusText;
            } else {
                var rsp = parseJson(xhr.response);
                loadSamples(rsp);
            }
            $scope.$apply("");
        };
    } else {
        // remove leading #
        $scope.gistid = gist.substring(1);
        $scope.samples = [{
            "description" : "Preselected sample",
            "id" : $scope.gistid
        }];
        $scope.loadGist();
    }

    if (!$scope.html) {
        $scope.html= templateHtml;  
        $scope.java = templateJava;  
    }
    $scope.classes = null;
    $scope.status = 'Initializing compiler...';
    if (typeof SharedWorker === 'undefined') {
      var w = new Worker('privatecompiler.js', 'javac');
      $scope.javac = w;
    } else {
        var w = new SharedWorker('sharedcompiler.js', 'javac');
        $scope.javac = w.port;
    }

    var JAVA_WORD = /[\w$]+/;
    $scope.javaHint = function (editor, fn, options) {
        var word = options && options.word || JAVA_WORD;
        var cur = editor.getCursor(), curLine = editor.getLine(cur.line);
        var start = cur.ch, end = start;
        while (start && word.test(curLine.charAt(start - 1)))
            --start;
        var pref = start !== end && curLine.slice(start, end);
        while (end < curLine.length && word.test(curLine.charAt(end)))
            ++end;

        $scope.pendingJavaHintInfo = {callback: fn, from: CodeMirror.Pos(cur.line, start), to: CodeMirror.Pos(cur.line, end), prefix: pref};
        $scope.post('autocomplete');
    };
    CodeMirror.registerHelper("hint", "clike", $scope.javaHint);

    $scope.javac.onmessage = function(ev) {
        var obj = ev.data;
        $scope.status = obj.status;
        if (obj.type === 'autocomplete') {
            if ($scope.pendingJavaHintInfo && obj.completions) {
                var list;
                if ($scope.pendingJavaHintInfo.prefix) {
                    var pref = $scope.pendingJavaHintInfo.prefix;
                    list = [];
                    for(var i = 0; i < obj.completions.length; ++i) {
                        if (obj.completions[i].slice(0, pref.length) === pref)
                            list[list.length] = obj.completions[i];
                    }
                } else {
                    list = obj.completions;
                }
                $scope.pendingJavaHintInfo.callback({list: list, from: $scope.pendingJavaHintInfo.from, to: $scope.pendingJavaHintInfo.to});
            }
            $scope.pendingJavaHintInfo = null;
        } else if (obj.errors.length === 0 && 
            (obj.type === "compile" || obj.type === "checkForErrors")
        ) {
            $scope.errors = null;
            var editor = document.getElementById("editorJava").codeMirror;   
            editor.clearGutter( "issues" );
            if (obj.classes !== null && obj.classes.length > 0) {
                $scope.classes = obj.classes;
                $scope.run();
            } else {
                $scope.classes = null;
            }
        } else {
            $scope.classes = null;
            $scope.fail(obj.errors);
        }
        $scope.javac.running = false;
        if ($scope.javac.pending) {
            $scope.javac.pending = false;
            $scope.post();
        }
        $scope.$apply("");
    };
    $scope.post = function(t) {
        t = t || 'checkForErrors';
        if ($scope.javac.running) {
            $scope.javac.pending = true;
        } else {
            var editor = document.getElementById("editorJava").codeMirror;
            var off = editor.indexFromPos(t === 'autocomplete' ? $scope.pendingJavaHintInfo.from : editor.getCursor());
            $scope.javac.postMessage({ type : t, html : $scope.html, java : $scope.java, offset : off});
            $scope.javac.running = true;
            if ($scope.status.indexOf('Init') < 0) {
                $scope.status = 'Compiling...';
                $scope.$apply("");
            }
        }
        $scope.classes = null;
        localStorage.gistid = $scope.gistid;
        localStorage.java = $scope.java;
        localStorage.html = $scope.html;
    };

    CodeMirror.commands.autocomplete = function(cm) {
        CodeMirror.showHint(cm, null, {async: true});
    };

    $scope.$watch( "html", $scope.debounce( $scope.post, 500 ) );
    $scope.$watch( "java", $scope.debounce( $scope.post, 500 ) );

}
