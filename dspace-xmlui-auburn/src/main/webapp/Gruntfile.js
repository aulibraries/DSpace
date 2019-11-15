/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
// Generated on 2013-11-09 using generator-webapp 0.4.3
'use strict';

module.exports = function (grunt) {

    // load all grunt tasks
    require('load-grunt-tasks')(grunt);

    grunt.initConfig({
        handlebars: {
            compile: {
                options: {
                    namespace: "DSpace.templates",
                    processName: function(filePath) {
                        return filePath.replace(/^templates\//, '').replace(/\.handlebars$/, '').replace(/\.hbs$/, '');
                    }
                },
                files: {
                    "scripts/templates.js": ["templates/*.handlebars", "templates/*.hbs"]
                }
            }
        },
    });

    grunt.registerTask('default', [
        'handlebars'
    ]);
};
