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
function parseJson(s) {
  if (typeof s === 'string') {
    return JSON.parse(s);
  } else {
    return s;
  }
}

function updateGist(token, reply) {
    if (reply.error) {
      alert("Can't update gist, error: " + reply.error);
      return;
    }
    if (!reply.gist || !reply.user) {
      return;
    }

    if (reply.gist.user.login !== reply.user.login) {
      var id = reply.gist.id;
      reply.gist = null;
      forkGist(token, id, reply);
      return;
    }
    var id = reply.gist.id;
    
    var res = {
        "files" : reply.gist.files
    };
    for (var f in res.files) {
        if (f.search(/\.html$/g) >= 0 && localStorage.html) {
            res.files[f].content = localStorage.html;
            delete localStorage.html;
        }
        if (f.search(/\.java$/g) >= 0 && localStorage.java) {
            res.files[f].content = localStorage.java;
            delete localStorage.java;
        }
    }
    if (localStorage.html) {
        res.files["index.html"] = { 
            "content" : localStorage.html
        };
    }
    if (localStorage.java) {
        res.files["Sample.java"] = { 
            "content" : localStorage.java
        };
    }

    var xhr = new XMLHttpRequest();
    xhr.open("PATCH", "https://api.github.com/gists/" + id + "?access_token=" + token);
    xhr.send(JSON.stringify(res));
    xhr.onreadystatechange = function(ev) {
      if (xhr.readyState !== 4) {
        return;
      }
      if (xhr.status !== 200) {
        alert('Something went wrong: ' + xhr.status);
        return;
      }
      localStorage.html = null;
      localStorage.java = null;
      alert('Gist saved as https://gist.github.com/jtulach/' + id + ' - keep the opened URL');
      window.location.href = 'http://dew.apidesign.org/dew/#' + id;
    };
}

function getGist(token, id, reply) {
    var xhr = new XMLHttpRequest();
    xhr.open("GET", "https://api.github.com/gists/" + id + "?access_token=" + token);
/*    xhr.responseType = "json"; */
    xhr.send();
    xhr.onreadystatechange = function(ev) {
      if (xhr.readyState !== 4) {
        return;
      }
      if (xhr.status !== 200) {
        reply.error = xhr.status;
      } else {
        reply.gist = parseJson(xhr.response);
      }
      reply.always(token, reply);
    };
} 
function forkGist(token, id, reply) {
    var xhr = new XMLHttpRequest();
    xhr.open("POST", "https://api.github.com/gists/" + id + "/forks?access_token=" + token);
/*    xhr.responseType = "json"; */
    xhr.send();
    xhr.onreadystatechange = function(ev) {
      if (xhr.readyState !== 4) {
        return;
      }
      if (xhr.status < 200 || xhr.status > 299) {
        reply.error = xhr.status;
      } else {
        reply.gist = parseJson(xhr.response);
      }
      reply.always(token, reply);
    };
}


function getUser(token, reply) {
    var xhr = new XMLHttpRequest();
    xhr.open("GET", "https://api.github.com/user" + "?access_token=" + token);
/*    xhr.responseType = "json"; */
    xhr.send();
    xhr.onreadystatechange = function(ev) {
      if (xhr.readyState !== 4) {
        return;
      }
      if (xhr.status !== 200) {
        reply.error = xhr.status;
      } else {
        reply.user = parseJson(xhr.response);
      }
      reply.always(token, reply);
    };
}


function authorize() {
  var gistid = parse("state", window.location.search);
  if (gistid === null) {
    return;
  }
    
  var url = 'https://github.com/login/oauth/' + 'authorize';
  url += '?client_id=13479cb2e9dd5f762848';
  url += '&scope=gist';
  url += '&state=' + gistid;

  window.location.href = url;
}

function parse(what,where) {
  var arr = where.match(new RegExp(what + '=[^&]*'));
  return arr !== null && arr.length > 0 ? arr[0].substring(what.length + 1) : null;
}

var code = parse('code', window.location.search);
var state = parse("state", window.location.search);

if (code && state) {
   var xhr = new XMLHttpRequest();
   xhr.open("POST", "http://dew.apidesign.org/access.php?code=" + code);
   xhr.send();
   xhr.onreadystatechange = function(ev) {
     if (xhr.readyState !== 4) {
       return;
     }
     if (xhr.status !== 200) {
       alert('POST failed: ' + xhr.status + " txt: " + xhr.statusText + " resp: " + xhr.responseText);
       return;
     }
     var token = xhr.responseText;
     var reply = {};
     reply.always = updateGist;
     
     getGist(token, state, reply);
     getUser(token, reply);
   }
}



