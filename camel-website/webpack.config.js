const webpack = require('webpack');
const path = require('path');
const AssetsPlugin = require('assets-webpack-plugin');
const CleanWebpackPlugin = require('clean-webpack-plugin');
const ExtractTextPlugin = require('extract-text-webpack-plugin');

const define = new webpack.DefinePlugin({
  'process.env': {
    NODE_ENV: JSON.stringify(process.env.NODE_ENV),
    BROWSERSLIST: ['> 1%', 'last 2 versions'],
  },
});

const extractCSS = new ExtractTextPlugin({
  filename: '../css/[name].[contenthash].css',
});

const assetsManifest = new AssetsPlugin({
  filename: 'assets.json',
  path: path.join(__dirname, 'data'),
  fullPath: false,
});

const cleanBuild = new CleanWebpackPlugin(['static/css/*', 'static/js/*'], {
  watch: true,
});

module.exports = {
  entry: {
    site: path.join(__dirname, 'src/scripts', 'site.js'),
  },
  output: {
    filename: '[name].[chunkhash].js',
    path: path.join(__dirname, 'static', 'js'),
  },
  module: {
    rules: [
      {
        test: /\.js$/,
        include: path.join(__dirname, 'src/scripts'),
        use: {
          loader: 'babel-loader',
          options: {
            presets: ['es2015'],
          },
        },
      }, {
        test: /\.scss$/,
        use: extractCSS.extract({
          fallback: 'style-loader',
          use: [{
            loader: 'css-loader',
          }, {
            loader: 'sass-loader',
          }],
        }),
      },
    ],
  },
  resolve: {
    extensions: ['*', '.js', '.scss'],
  },
  plugins: [cleanBuild, define, extractCSS, assetsManifest],
};
