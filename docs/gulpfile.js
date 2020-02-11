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
const { dest, series, parallel, src, symlink } = require('gulp');
const del = require('del');
const inject = require('gulp-inject');
const map = require('map-stream')
const path = require('path');
const rename = require('gulp-rename');
const replace = require('gulp-replace');
const sort = require('gulp-sort');
const through2 = require('through2');
const File = require('vinyl')
const fs = require('fs');

function deleteComponentSymlinks() {
    return del(['components/modules/ROOT/pages/*', '!components/modules/ROOT/pages/index.adoc']);
}

function deleteComponentImageSymlinks() {
    return del(['components/modules/ROOT/assets/images/*']);
}

function createComponentSymlinks() {
    return src(['../core/camel-base/src/main/docs/*.adoc', '../core/camel-xml-jaxp/src/main/docs/*.adoc', '../components/{*,*/*}/src/main/docs/*.adoc'])
        .pipe(map((file, done) => {
            // this flattens the output to just .../pages/....adoc
            // instead of .../pages/camel-.../src/main/docs/....adoc
            file.base = path.dirname(file.path);
            done(null, file);
        }))
        // Antora disabled symlinks, there is an issue open
        // https://gitlab.com/antora/antora/issues/188
        // to reinstate symlink support, until that's resolved
        // we'll simply copy over instead of creating symlinks
        // .pipe(symlink('components/modules/ROOT/pages/', {
        //     relativeSymlinks: true
        // }));
        // uncomment above .pipe() and remove the .pipe() below
        // when antora#188 is resolved
        .pipe(insertSourceAttribute())
        .pipe(dest('components/modules/ROOT/pages/'));
}

function createComponentImageSymlinks() {
    return src('../components/{*,*/*}/src/main/docs/*.png')
        .pipe(map((file, done) => {
            // this flattens the output to just .../pages/....adoc
            // instead of .../pages/camel-.../src/main/docs/....adoc
            file.base = path.dirname(file.path);
            done(null, file);
        }))
        // Antora disabled symlinks, there is an issue open
        // https://gitlab.com/antora/antora/issues/188
        // to reinstate symlink support, until that's resolved
        // we'll simply copy over instead of creating symlinks
        // .pipe(symlink('components/modules/ROOT/pages/', {
        //     relativeSymlinks: true
        // }));
        // uncomment above .pipe() and remove the .pipe() below
        // when antora#188 is resolved
        .pipe(dest('components/modules/ROOT/assets/images/'));
}

function deleteUserManualSymlinks() {
    return del(['user-manual/modules/ROOT/pages/*-eip.adoc', 'user-manual/modules/ROOT/pages/*-language.adoc']);
}

function createUserManualSymlinks() {
    return src(['../core/camel-base/src/main/docs/*.adoc', '../core/camel-xml-jaxp/src/main/docs/*.adoc', '../core/camel-core-engine/src/main/docs/eips/*.adoc'])
        // Antora disabled symlinks, there is an issue open
        // https://gitlab.com/antora/antora/issues/188
        // to reinstate symlink support, until that's resolved
        // we'll simply copy over instead of creating symlinks
        // .pipe(symlink('user-manual/modules/ROOT/pages/', {
        //     relativeSymlinks: true
        // }));
        // uncomment above .pipe() and remove the .pipe() below
        // when antora#188 is resolved
        .pipe(insertSourceAttribute())
        .pipe(dest('user-manual/modules/ROOT/pages/'));
}

function titleFrom(file) {
    const maybeName = /(?:=|#) (.*)/.exec(file.contents.toString())
    if (maybeName == null) {
        throw new Error(`${file.path} doesn't contain Asciidoc heading ('= <Title>') or ('# <Title')`);
    }

    return maybeName[1];
}

function insertGeneratedNotice() {
    return inject(src('./generated.txt'), {
        name: 'generated',
        removeTags: true,
        transform: (filename, file) => {
            return file.contents.toString('utf8');
        }
    });
}

function insertSourceAttribute() {
    return replace(/^= .+/m, function(match) {
        return `${match}\n:page-source: ${path.relative('..', this.file.path)}`;
    });
}

function createComponentNav() {
    return src('component-nav.adoc.template')
        .pipe(insertGeneratedNotice())
        .pipe(inject(src(['../core/camel-base/src/main/docs/*-component.adoc', '../components/{*,*/*}/src/main/docs/*.adoc']).pipe(sort()), {
            removeTags: true,
            transform: (filename, file) => {
                const filepath = path.basename(filename);
                const title = titleFrom(file);
                return `* xref:${filepath}[${title}]`;
            }
        }))
        .pipe(rename('nav.adoc'))
        .pipe(dest('components/modules/ROOT/'))
}

function createUserManualNav() {
    return src('user-manual-nav.adoc.template')
        .pipe(insertGeneratedNotice())
        .pipe(inject(src('../core/camel-base/src/main/docs/*.adoc').pipe(sort()), {
            removeTags: true,
            name: 'languages',
            transform: (filename, file) => {
                const filepath = path.basename(filename);
                const title = titleFrom(file);
                return ` ** xref:${filepath}[${title}]`;
            }
        }))
        .pipe(inject(src('../core/camel-core-engine/src/main/docs/eips/*.adoc').pipe(sort()), {
            removeTags: true,
            name: 'eips',
            transform: (filename, file) => {
                const filepath = path.basename(filename);
                const title = titleFrom(file);
                return ` ** xref:${filepath}[${title}]`;
            }
        }))
        .pipe(rename('nav.adoc'))
        .pipe(dest('user-manual/modules/ROOT/'))
}

const extractExamples = function(file, enc, next) {
    const asciidoc = file.contents.toString();
    const includes = /(?:include::\{examplesdir\}\/)([^[]+)/g;
    let example;
    let exampleFiles = new Set()
    while (example = includes.exec(asciidoc)) {
        let examplePath = path.resolve(path.join('..', example[1]));
        exampleFiles.add(examplePath);
    }

    exampleFiles.forEach(examplePath => this.push(new File({
        base: path.resolve('..'),
        path: examplePath,
        contents: fs.createReadStream(examplePath)
    })));

    return next();
}

function deleteExamples(){
    return del(['user-manual/modules/ROOT/examples/', 'components/modules/ROOT/examples/']);
}

function createUserManualExamples() {
    return src('user-manual/modules/ROOT/**/*.adoc')
        .pipe(through2.obj(extractExamples))
        .pipe(dest('user-manual/modules/ROOT/examples/'));
}

function createComponentExamples() {
    return src('../components/{*,*/*}/src/main/docs/*.adoc')
        .pipe(through2.obj(extractExamples))
        .pipe(dest('components/modules/ROOT/examples/'));
}

const symlinks = parallel(
    series(deleteComponentSymlinks, createComponentSymlinks),
    series(deleteComponentImageSymlinks, createComponentImageSymlinks),
    series(deleteUserManualSymlinks, createUserManualSymlinks)
);
const nav = parallel(createComponentNav, createUserManualNav);
const examples = series(deleteExamples, createUserManualExamples, createComponentExamples);

exports.symlinks = symlinks;
exports.nav = nav;
exports.examples = examples;
exports.default = series(symlinks, nav, examples);
