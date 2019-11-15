/* 
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */


const path = require("path");
const webpack = require("webpack");
//const {CleanWebpackPlugin} = require("clean-webpack-plugin");
const UglifyJsPlugin = require("uglifyjs-webpack-plugin");
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
        },
        minimizer: [
            new UglifyJsPlugin({
                cache: false,
                parallel: true,
                sourceMap: true
            }),
            new OptimizeCSSAssetsPlugin({})
        ]
    },
    plugins: [
        new MiniCssExtractPlugin({
            filename: "./styles/main.css"            
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
