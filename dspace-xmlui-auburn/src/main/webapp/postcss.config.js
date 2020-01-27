/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */


module.exports = {
    plugins: function() {
        return [
            require("precss"),
            require("autoprefixer"),
            require("cssnano")
        ]
    }
};
