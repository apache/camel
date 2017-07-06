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
const gulp = require('gulp');
const rename = require('gulp-rename');
const chmod = require('gulp-chmod');
const replace = require('gulp-replace');

const version = process.env.npm_package_version.replace(/-.*/, '');

gulp.task('docs', ['component-doc']);

gulp.task('component-doc', () => {
  gulp.src('../components/readme.adoc')
    .pipe(replace(/link:.*\/(.*).adoc(\[.*)/g, `link:components/${version}/$1$2`))
    .pipe(rename('components.adoc'))
    .pipe(gulp.dest('content'));
  gulp.src('../components/**/src/main/docs/*.adoc')
    .pipe(rename({dirname: ''}))
    .pipe(gulp.dest(`content/components/${version}`));
});

gulp.task('asciidoctor-shim', () => {
  gulp.src('src/scripts/asciidoctor-shim.js')
    .pipe(rename('asciidoctor'))
    .pipe(chmod(0o755))
    .pipe(gulp.dest('node_modules/.bin/'));
});

gulp.task('default', ['docs', 'asciidoctor-shim']);

gulp.task('watch', () => {
  gulp.watch('../**/*.adoc', ['docs']);
});

