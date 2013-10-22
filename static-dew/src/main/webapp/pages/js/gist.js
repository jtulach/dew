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

'use strict';

(function(global) {

    var Base64 = {
        to : function( text ){ return window.btoa(text); },
        from : function( text ){ return window.atob(text); }
    };

    global.GitHub = function( http, url ) {
        
        this.http = http;
        this.url = url || "https://api.github.com/";
        this.headers = {};
        
    };
    
    global.GitHub.prototype.login = function( username, password ) {
        
        var authorization = null;
        
        if ( username && password ) {
            authorization = 'Basic ' + Base64.to(username + ':' + password);
        }
        else if ( auth === 'oauth' && username ) {
            authorization = 'token ' + options.token;
        }
        
        if ( authorization === null ) {
            return null;
        }
        
        /// this.headers.Authorization = "Basic aHJlYmVqazprcjBwYWNlaw==";
        /// this.headers = {"Authorization" : authorization};
        this.headers["Authorization"] = authorization;
        this.readUser();
        
        return this.user;
    };
    
    global.GitHub.prototype.logout = function() {
        delete this.headers['Authorization'];
        this.user = undefined;        
    };

    global.GitHub.prototype.readUser = function() {
        this.user = this.http({
                url: this.url + "user",
                headers: this.headers, 
                method: "GET"
            }).then(function(data) {
                var user = angular.fromJson(data.data);
                return user;
            });
        
        
//        return this.user = this.http( config ).then(function(data) {
//            return ( global.Json.from(data.data) );
//        });
    };

    /* Tries to read gist of given id. */
    global.GitHub.prototype.gist = function(id) {
        return this.http({url: this.url+ "gists/" + id, // + "?callback=JSON_CALLBACK",
            method: "GET",
            headers: this.headers
        });
    };

    /* Reads list of gist for authenticated user 
     * XXX really read all of them not just first page 
     */
    global.GitHub.prototype.gists = function() {
        return this.http({url: this.url + "gists",
            method: "GET",
            headers: this.headers
        });
    };

    global.GitHub.prototype.createGist = function(gist) {
        return this.http({url: this.url + "gists",
            method: "POST",
            headers: this.headers,
            data: gist
        });
    };

    global.GitHub.prototype.updateGist = function(id, gist) {
        return this.http({url: this.url + "gists/" + id,
            method: "PATCH",
            headers: this.headers,
            data : gist
        });
    };
})(window);
