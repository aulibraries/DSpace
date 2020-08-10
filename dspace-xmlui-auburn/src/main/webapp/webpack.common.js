/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */


const path = require("path");
const webpack = require("webpack");
const MiniCssExtractPlugin = require("mini-css-extract-plugin");
const OptimizeCSSAssetsPlugin = require("optimize-css-assets-webpack-plugin");

const loaders = {
    css: {
        loader: "css-loader"
    },
    postcss: {
        loader: "postcss-loader"        
    },
    sass: {
        loader: "sass-loader"
    },
    style: {
        loader: "style-loader"
    }
};

module.exports = {
    entry: "./app.js",
    output: {
        path: path.resolve(__dirname),
        filename: "./scripts/theme.js"
    },
    optimization: {
        splitChunks: {
            cacheGroups: {
                styles: {
                    name: "main",
                    test: /\.css$/i,
                    chunks: "all",
                    enforce: true
                    
                }
            }
        }
    },
    plugins: [
        new MiniCssExtractPlugin({
            filename: "./styles/main.css"            
        }),
        new webpack.ProvidePlugin({
            $: 'jquery',
            jQuery: 'jquery',
            'window.jQuery': 'jquery'
        }),
        /* new CleanWebpackPlugin(["scripts/theme.js", "styles/main.css"], { 
            verbose: true, 
            dry: false 
        }) */
    ],
    module: {
        rules: [
            {
                test: /\.scss$/i,
                use: [
                    MiniCssExtractPlugin.loader,
                    loaders.css,
                    loaders.postcss,
                    loaders.sass
                ]
            },
            {
                test: /\.js$/i,
                use: ["source-map-loader"],
                enforce: "pre"
            }
        ]
    }
    
};
