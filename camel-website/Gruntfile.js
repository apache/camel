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
