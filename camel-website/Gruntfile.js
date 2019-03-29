/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
module.exports = function(grunt) {

    // Project configuration.
    grunt.initConfig({

        'http-server': {
            dev: {
                root: 'target/classes/user-manual/',
                port: 8282,
                host: "0.0.0.0",
                showDir: false,
                autoIndex: true,
                ext: "html",
                runInBackground: true,
                logFn: function(req, res, error) { },


                // specify a logger function. By default the requests are 
                // sent to stdout. 
                // logFn: function(req, res, error) { },

                // Proxies all requests which can't be resolved locally to the given url 
                // Note this this will disable 'showDir' 
                // proxy: "http://someurl.com",
                // Tell grunt task to open the browser 
                // openBrowser : false
            }

        },
        linkChecker: {
            dev: {
                site: 'localhost',
                options: {
                    initialPort: 8282
                }
            }
        }
    });

    grunt.loadNpmTasks('grunt-http-server');
    grunt.loadNpmTasks('grunt-link-checker');

    // Default task(s).
    grunt.registerTask('default', ['http-server', 'linkChecker']);

};
