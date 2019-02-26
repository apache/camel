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
const { dest, series, src, symlink } = require('gulp');
const del = require('del');
const inject = require('gulp-inject');
const map = require('map-stream')
const path = require('path');
const rename = require('gulp-rename');
const sort = require('gulp-sort');

function deleteSymlinks() {
    return del(['components/modules/ROOT/pages/*', '!components/modules/ROOT/pages/index.adoc']);
}

function createSymlinks() {
    return src('../components/*/src/main/docs/*.adoc')
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
        .pipe(dest('components/modules/ROOT/pages/'));
}

function nav() {
    return src('nav.adoc.template')
        .pipe(inject(src('../components/*/src/main/docs/*.adoc').pipe(sort()), {
            removeTags: true,
            transform: (filename, file) => {
                const filepath = path.basename(filename);
                const maybeName = /(?:==|##) (.*)/.exec(file.contents.toString())
                if (maybeName == null) {
                    throw new Error(`${file.path} doesn't contain Asciidoc heading ('== <Title>') or ('## <Title')`);
                }
                return `* xref:${filepath}[${maybeName[1]}]`;
            }
        }))
        .pipe(rename('nav.adoc'))
        .pipe(dest('components/modules/ROOT/'))
}

const symlinks = series(deleteSymlinks, createSymlinks);

exports.symlinks = symlinks;
exports.nav = nav;
exports.default = series(symlinks, nav);
