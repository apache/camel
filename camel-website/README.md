# Camel Website

This is a site generator project for Apache Camel. It generates static HTML and
resources that are to be published. Tools used to generate the website:
 - [Gulp](http://gulpjs.com/) a task automation tool. It is used to gather
   documentation files from the Camel source tree and filter and copy them into
   the `content` folder.
 - [Hugo](https://gohugo.io) a static site generator. Simplified, it takes the
   documentation from the `content` folder and applies templates from `layouts`
   folder and together with any resources in `static` folder generates output in
   the `public` folder.
 - [Yarn](https://yarnpkg.io) - JavaScript dependency management and script
   runner. Used to bring in all tooling (Gulp, Hugo, Webpack, ...) and other
   dependencies (Skeleton CSS framework for example) and run `build` and `watch`
   scripts.
 - [Webpack](https://webpack.js.org/) - JavaScript and CSS module bundler, it
   generates JavaScript in `static/js` and CSS in `static/css` bundles from
   `src/scripts` and `src/stylesheets` respectively.

## Building the website

To build the website run:

    $ yarn // needed only once, or if dependencies change
    $ yarn build // to perform the build

This should generate the website in the `public` folder.

## Working on the website

When working on the website it is nice to see the effects of the change
immediately, to that end you can run:

    $ yarn // needed only once, or if dependencies change
    $ yarn build // to perform the build
    $ yarn serve // serve the website on http://localhost:1313 and react on \
                    changed files

If a file is changed tools react on that change and a script present in the
website performs a reload (livereload).
