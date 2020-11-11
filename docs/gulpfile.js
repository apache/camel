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

/* eslint-disable-next-line no-unused-vars */
const { dest, series, parallel, src, symlink } = require('gulp')
const del = require('del')
const filter = require('gulp-filter')
const inject = require('gulp-inject')
const map = require('map-stream')
const path = require('path')
const rename = require('gulp-rename')
const replace = require('gulp-replace')
const sort = require('gulp-sort')
const through2 = require('through2')
const File = require('vinyl')
const fs = require('fs')

function deleteComponentSymlinks () {
  return del([
    'components/modules/ROOT/pages/*',
    '!components/modules/ROOT/pages/index.adoc',
  ])
}

function deleteComponentImageSymlinks () {
  return del(['components/modules/ROOT/assets/images/*'])
}

function createComponentSymlinks () {
  return (
    src([
      '../core/camel-base/src/main/docs/*-component.adoc',
      '../components/{*,*/*}/src/main/docs/*-component.adoc',
      '../components/{*,*/*}/src/main/docs/*-summary.adoc',
    ])
      .pipe(
        map((file, done) => {
          // this flattens the output to just .../pages/....adoc
          // instead of .../pages/camel-.../src/main/docs/....adoc
          file.base = path.dirname(file.path)
          done(null, file)
        })
      )
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
      .pipe(dest('components/modules/ROOT/pages/'))
  )
}

function createComponentOthersSymlinks () {
  const f = filter([
    '**',
    '!**/*-language.adoc',
    '!**/*-dataformat.adoc',
    '!**/*-component.adoc',
    '!**/*-summary.adoc',
  ])
  return (
    src([
      '../core/camel-base/src/main/docs/*.adoc',
      '../core/camel-main/src/main/docs/*.adoc',
      '../components/{*,*/*}/src/main/docs/*.adoc',
    ])
      .pipe(f)
      .pipe(
        map((file, done) => {
          // this flattens the output to just .../pages/....adoc
          // instead of .../pages/camel-.../src/main/docs/....adoc
          file.base = path.dirname(file.path)
          done(null, file)
        })
      )
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
      .pipe(dest('components/modules/others/pages/'))
  )
}

function createComponentDataFormatSymlinks () {
  return (
    src(['../components/{*,*/*}/src/main/docs/*-dataformat.adoc'])
      .pipe(
        map((file, done) => {
          // this flattens the output to just .../pages/....adoc
          // instead of .../pages/camel-.../src/main/docs/....adoc
          file.base = path.dirname(file.path)
          done(null, file)
        })
      )
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
      .pipe(dest('components/modules/dataformats/pages/'))
  )
}

function createComponentLanguageSymlinks () {
  return (
    src(['../components/{*,*/*}/src/main/docs/*-language.adoc'])
      .pipe(
        map((file, done) => {
          // this flattens the output to just .../pages/....adoc
          // instead of .../pages/camel-.../src/main/docs/....adoc
          file.base = path.dirname(file.path)
          done(null, file)
        })
      )
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
      .pipe(dest('components/modules/languages/pages/'))
  )
}

function createComponentImageSymlinks () {
  return (
    src('../components/{*,*/*}/src/main/docs/*.png')
      .pipe(
        map((file, done) => {
          // this flattens the output to just .../pages/....adoc
          // instead of .../pages/camel-.../src/main/docs/....adoc
          file.base = path.dirname(file.path)
          done(null, file)
        })
      )
      // Antora disabled symlinks, there is an issue open
      // https://gitlab.com/antora/antora/issues/188
      // to reinstate symlink support, until that's resolved
      // we'll simply copy over instead of creating symlinks
      // .pipe(symlink('components/modules/ROOT/pages/', {
      //     relativeSymlinks: true
      // }));
      // uncomment above .pipe() and remove the .pipe() below
      // when antora#188 is resolved
      .pipe(dest('components/modules/ROOT/assets/images/'))
  )
}

function titleFrom (file) {
  var maybeName = /(?::docTitle: )(.*)/.exec(file.contents.toString())
  if (maybeName === null) {
    //TODO investigate these... why dont they have them?
    // console.warn(`${file.path} doesn't contain Asciidoc docTitle attribute (':docTitle: <Title>'`);
    maybeName = /(?:=|#) (.*)/.exec(file.contents.toString())
    if (maybeName === null) {
      throw new Error(
        `${file.path} also doesn't contain Asciidoc heading ('= <Title>') or ('# <Title')`
      )
    }
  }

  return maybeName[1]
}

function groupFrom (file) {
  var groupName = /(?::group: )(.*)/.exec(file.contents.toString())
  if (groupName !== null) return groupName[1]
  return null
}

function compare (file1, file2) {
  if (file1 === file2) return 0
  return titleFrom(file1).toUpperCase() < titleFrom(file2).toUpperCase()
    ? -1
    : 1
}

function compareComponents (file1, file2) {
  if (file1 === file2) return 0
  const group1 = groupFrom(file1) ? groupFrom(file1).toUpperCase() : null
  const group2 = groupFrom(file2) ? groupFrom(file2).toUpperCase() : null
  const title1 = titleFrom(file1).toUpperCase()
  const title2 = titleFrom(file2).toUpperCase()

  if (group1 === null && group2 === null) return title1 < title2 ? -1 : 1
  if (group2 !== null && group1 === null) {
    if (title1 === group2) return -1
    return title1 < group2 ? -1 : 1
  }
  if (group1 !== null && group2 === null) {
    if (title2 === group1) return 1
    return group1 < title2 ? -1 : 1
  }
  if (group1 !== null && group2 !== null) {
    if (group1 === group2) return title1 < title2 ? -1 : 1
    return group1 < group2 ? -1 : 1
  }
}

function insertGeneratedNotice () {
  return inject(src('./generated.txt'), {
    name: 'generated',
    removeTags: true,
    transform: (filename, file) => {
      return file.contents.toString('utf8')
    },
  })
}

function insertSourceAttribute () {
  return replace(/^= .+/m, function (match) {
    return `${match}\n//THIS FILE IS COPIED: EDIT THE SOURCE FILE:\n:page-source: ${path.relative('..', this.file.path)}`
  })
}

function createComponentNav () {
  return src('component-nav.adoc.template')
    .pipe(insertGeneratedNotice())
    .pipe(
      inject(
        src([
          'components/modules/ROOT/pages/**/*.adoc',
          '!components/modules/ROOT/pages/index.adoc',
        ]).pipe(sort(compareComponents)),
        {
          removeTags: true,
          transform: (filename, file) => {
            const filepath = path.basename(filename)
            const title = titleFrom(file)
            if (groupFrom(file) !== null) { return `*** xref:${filepath}[${title}]` }
            return `** xref:${filepath}[${title}]`
          },
        }
      )
    )
    .pipe(rename('nav.adoc'))
    .pipe(dest('components/modules/ROOT/'))
}

function createComponentOthersNav () {
  return src('component-others-nav.adoc.template')
    .pipe(insertGeneratedNotice())
    .pipe(
      inject(
        src([
          'components/modules/others/pages/**/*.adoc',
          '!components/modules/others/pages/index.adoc',
        ]).pipe(sort(compare)),
        {
          removeTags: true,
          transform: (filename, file) => {
            const filepath = path.basename(filename)
            const title = titleFrom(file)
            return `** xref:${filepath}[${title}]`
          },
        }
      )
    )
    .pipe(rename('nav.adoc'))
    .pipe(dest('components/modules/others/'))
}

function createComponentDataFormatsNav () {
  return src('component-dataformats-nav.adoc.template')
    .pipe(insertGeneratedNotice())
    .pipe(
      inject(
        src([
          'components/modules/dataformats/pages/**/*.adoc',
          '!components/modules/dataformats/pages/index.adoc',
        ]).pipe(sort(compare)),
        {
          removeTags: true,
          transform: (filename, file) => {
            const filepath = path.basename(filename)
            const title = titleFrom(file)
            return `** xref:dataformats:${filepath}[${title}]`
          },
        }
      )
    )
    .pipe(rename('nav.adoc'))
    .pipe(dest('components/modules/dataformats/'))
}

function createComponentLanguagesNav () {
  return src('component-languages-nav.adoc.template')
    .pipe(insertGeneratedNotice())
    .pipe(
      inject(
        src([
          'components/modules/languages/pages/**/*.adoc',
          '!components/modules/languages/pages/index.adoc',
        ]).pipe(sort(compare)),
        {
          removeTags: true,
          transform: (filename, file) => {
            const filepath = path.basename(filename)
            const title = titleFrom(file)
            return `** xref:languages:${filepath}[${title}]`
          },
        }
      )
    )
    .pipe(rename('nav.adoc'))
    .pipe(dest('components/modules/languages/'))
}

function createEIPNav () {
  const f = filter(['**', '!**/enterprise-integration-patterns.adoc'])
  return src('eip-nav.adoc.template')
    .pipe(insertGeneratedNotice())
    .pipe(
      inject(
        src('../core/camel-core-engine/src/main/docs/modules/eips/pages/*.adoc')
          .pipe(f)
          .pipe(sort(compare)),
        {
          removeTags: true,
          name: 'eips',
          transform: (filename, file) => {
            const filepath = path.basename(filename)
            const title = titleFrom(file)
            return ` ** xref:eips:${filepath}[${title}]`
          },
        }
      )
    )
    .pipe(rename('nav.adoc'))
    .pipe(dest('../core/camel-core-engine/src/main/docs/modules/eips/'))
}

const extractExamples = function (file, enc, next) {
  const asciidoc = file.contents.toString()
  const includes = /(?:include::\{examplesdir\}\/)([^[]+)/g
  let example
  const exampleFiles = new Set()
  while ((example = includes.exec(asciidoc))) {
    const examplePath = path.resolve(path.join('..', example[1]))
    exampleFiles.add(examplePath)
  }
  // (exampleFiles.size > 0) && console.log('example files', exampleFiles)
  exampleFiles.forEach((examplePath) =>
    this.push(
      new File({
        base: path.resolve('..'),
        path: examplePath,
        contents: fs.createReadStream(examplePath),
      })
    )
  )

  return next()
}

function deleteExamples () {
  return del([
    'user-manual/modules/ROOT/examples/',
    'user-manual/modules/faq/examples/',
    'components/modules/ROOT/examples/',
  ])
}

function createUserManualExamples () {
  return src('user-manual/modules/ROOT/**/*.adoc')
    .pipe(through2.obj(extractExamples))
    .pipe(dest('user-manual/modules/ROOT/examples/'))
}

function createFAQExamples () {
  return src('user-manual/modules/faq/**/*.adoc')
    .pipe(through2.obj(extractExamples))
    .pipe(dest('user-manual/modules/faq/examples/'))
}

function createEIPExamples () {
  return src('../core/camel-core-engine/src/main/docs/modules/eips/**/*.adoc')
    .pipe(through2.obj(extractExamples))
    .pipe(
      dest('../core/camel-core-engine/src/main/docs/modules/eips/examples/')
    )
}

function createUserManualLanguageExamples () {
  return src(
    '../core/camel-core-languages/src/main/docs/user-manual/modules/languages/**/*.adoc'
  )
    .pipe(through2.obj(extractExamples))
    .pipe(dest('user-manual/modules/ROOT/examples/'))
}

function createComponentExamples () {
  return src('../components/{*,*/*}/src/main/docs/*.adoc')
    .pipe(through2.obj(extractExamples))
    .pipe(dest('components/modules/ROOT/examples/'))
}

const symlinks = parallel(
  series(
    deleteComponentSymlinks,
    createComponentSymlinks,
    createComponentOthersSymlinks,
    createComponentDataFormatSymlinks,
    createComponentLanguageSymlinks
  ),
  series(deleteComponentImageSymlinks, createComponentImageSymlinks)
)
const nav = parallel(
  createComponentNav,
  createComponentOthersNav,
  createComponentDataFormatsNav,
  createComponentLanguagesNav,
  createEIPNav
)
const examples = series(
  deleteExamples,
  createUserManualExamples,
  createFAQExamples,
  createEIPExamples,
  createUserManualLanguageExamples,
  createComponentExamples
)

exports.symlinks = symlinks
exports.nav = nav
exports.examples = examples
exports.default = series(symlinks, nav, examples)
