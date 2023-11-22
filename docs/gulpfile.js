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

import File from 'vinyl'
import gulp from 'gulp'
import inject from 'gulp-inject'
import map from 'map-stream'
import path from 'path'
import rename from 'gulp-rename'
import sort from 'gulp-sort'
import through2 from 'through2'
import { deleteAsync } from 'del'

function titleFrom (file) {
  let maybeName = /(?::doctitle: )(.*)/.exec(file.contents.toString())
  if (maybeName === null) {
    //TODO investigate these... why dont they have them?
    // console.warn(`${file.path} doesn't contain Asciidoc doctitle attribute (':doctitle: <Title>'`);
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
  const groupName = /(?::group: )(.*)/.exec(file.contents.toString())
  if (groupName !== null) return groupName[1]
  return null
}

function compare (file1, file2) {
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
  return inject(gulp.src('./generated.txt'), {
    name: 'generated',
    removeTags: true,
    transform: (filename, file) => {
      return file.contents.toString('utf8')
    },
  })
}

// For (some) readability the literal object is used to configure
// the tasks in this form:
//
// taskName: {
//   kind: { // kind can be asciidoc, image, example or json
//     source: [...] | '...', // array or string with globs pointing to source files outside of the docs directory
//     destination: '...', // where to put the symbolic links to the source files
//     keep: [...], // optional array of files not to delete from the destination directory (defaults to ['index.adoc'])
//     filter: fn(content), // optional function to filter based on file content
//   }
// }
const sources = {
  components: {
    asciidoc: {
      source: [
        '../core/camel-base/src/main/docs/*-component.adoc',
        '../components/{*,*/*,*/*/*}/src/main/docs/*-component.adoc',
        '../components/{*,*/*}/src/main/docs/*-summary.adoc',
      ],
      destination: 'components/modules/ROOT/pages',
    },
    image: {
      source: '../components/{*,*/*}/src/main/docs/*.png',
      destination: 'components/modules/ROOT/images',
    },
    example: {
      source: [
        '../core/camel-base/src/main/docs/*.adoc',
        '../core/camel-main/src/main/docs/*.adoc',
        '../components/{*,*/*}/src/main/docs/*.adoc',
      ],
      destination: 'components/modules/ROOT/examples',
    },
    json: {
      source: [
        '../components/{*,*/*,*/*/*}/src/generated/resources/org/apache/camel/**/*.json',
      ],
      destination: 'components/modules/ROOT/examples/json',
      filter: (content) => JSON.parse(content).component, // check if there is a "component" key at the root
    },
  },
  dataformats: {
    asciidoc: {
      source: '../components/{*,*/*}/src/main/docs/*-dataformat.adoc',
      destination: 'components/modules/dataformats/pages',
    },
    json: {
      source: [
        '../components/{*,*/*,*/*/*}/src/generated/resources/org/apache/camel/**/*.json',
        '../core/camel-core-model/src/generated/resources/org/apache/camel/model/dataformat/*.json',
      ],
      destination: 'components/modules/dataformats/examples/json',
      filter: (content) => JSON.parse(content).dataformat, // check if there is a "dataformat" key at the root
    },
  },
  //IMPORTANT NOTE: some language adocs may also be copied by an undocumented process
  //from core/camel-core-languages and core/camel-xml-jaxp.
  languages: {
    asciidoc: {
      source: [
        '../components/{*,*/*}/src/main/docs/*-language.adoc',
        '../core/camel-core-languages/src/main/docs/modules/languages/pages/*-language.adoc',
        '../core/camel-xml-jaxp/src/main/docs/modules/languages/pages/*-language.adoc',
      ],
      destination: 'components/modules/languages/pages',
    },
    json: {
      source: [
        '../components/{*,*/*,*/*/*}/src/generated/resources/org/apache/camel/*/**/*.json',
        '../core/camel-core-languages/src/generated/resources/org/apache/camel/language/**/*.json',
        '../core/camel-core-model/src/generated/resources/org/apache/camel/model/language/*.json',
      ],
      destination: 'components/modules/languages/examples/json',
      filter: (content) => JSON.parse(content).language, // check if there is a "language" key at the root
    },
  },
  //TODO this newly copies several dsl pages. Is this correct?
  // (output edited) diff --git a/docs/components/modules/others/nav.adoc b/docs/components/modules/others/nav.adoc
  // +** xref:groovy-dsl.adoc[Groovy Dsl]
  // +** xref:js-dsl.adoc[JavaScript Dsl]
  // +** xref:java-xml-jaxb-dsl.adoc[Jaxb XML Dsl]
  // +** xref:kotlin-dsl.adoc[Kotlin Dsl]
  // +** xref:java-xml-io-dsl.adoc[XML Dsl]
  //These seem to have no content, just a non-xref link to the user manual,
  // where the dsls are not actually explained.  Should the sources be removed?
  others: {
    asciidoc: {
      source: [
        '../core/camel-base/src/main/docs/!(*-component|*-language|*-dataformat|*-summary).adoc',
        '../core/camel-main/src/main/docs/!(*-component|*-language|*-dataformat|*-summary).adoc',
        '../components/{*,*/*}/src/main/docs/!(*-component|*-language|*-dataformat|*-summary).adoc',
        '../dsl/**/src/main/docs/!(*-component|*-language|*-dataformat|*-summary).adoc',
        '../core/camel-xml-jaxp/src/generated/resources/*.json',
      ],
      destination: 'components/modules/others/pages',
      keep: [
        'index.adoc',
        'reactive-threadpoolfactory-vertx.adoc', // not part of any component
      ],
    },
    json: {
      source: [
        '../components/{*,*/*,*/*/*}/src/generated/resources/*.json',
      ],
      destination: 'components/modules/others/examples/json',
      filter: (content) => JSON.parse(content).other, // check if there is a "other" key at the root
    },
  },
  eips: {
    asciidoc: {
      destination: '../core/camel-core-engine/src/main/docs/modules/eips/pages',
      filter: (path) => !path.endsWith('enterprise-integration-patterns.adoc')
    },
    json: {
      source: [
        '../core/camel-core-model/src/generated/resources/org/apache/camel/model/**/*.json',
      ],
      destination: '../core/camel-core-engine/src/main/docs/modules/eips/examples/json',
      filter: (content) => {
        const json = JSON.parse(content)
        return json.model && json.model.label.includes('eip')
      },
    },
  },
  'manual:faq': {
    example: {
      source: 'user-manual/modules/faq/**/*.adoc',
      destination: 'user-manual/modules/faq/examples',
    },
  },
}

// Generates a tree of Gulp tasks it will be created from the data
// structure above. For example given:
//
// taskName: {
//   asciidoc: {
//     source: ...,
//     destination: ...
//   },
//   json: {
//     source: ...,
//     destination: ...,
//   }
// },
//
// gulp.parallel( // root containing multiple groupings (say, components, dataformats, eips...)
//   gulp.parallel( // tasks for the given taskName
//     gulp.series(clean:asciidoc:taskName, symlink:asciidoc:taskName, nav:asciidoc:taskName),
//     gulp.series(clean:json:taskName, symlink:json:taskName),
//   ),
//   gulp.parallel( // tasks configured for a different group
//     ...
//   )
// )
//
// Or when run with `yarn gulp --tasks` something like:
// ├─┬ default
// │ └─┬ <parallel>
// │   ├─┬ <series>
// │   │ ├── clean:asciidoc:taskName
// │   │ ├── symlink:asciidoc:taskName
// │   │ └── nav:asciidoc:taskName
// │   └─┬ <series>
// │     ├── clean:json:taskName
// │     └── symlink:json:taskName
//
// Where each `*:taskName` is a Gulp function task performing the
// following:
//  * `clean:*` task deletes symbolic links,
//  * `symlink:*` recreates symbolic links pointing to the source
//    files in the project tree,
//  * `nav:*` regenerates `nav.adoc` files.
// There are two different symlink operations, one for .adoc files
// that strips base directory and for examples that perserves it
const sourcesMap = new Map(Object.entries(sources))
// type is 'components', 'dataformats', ...
// definition is the value under that key, e.g.:
// {
//   asciidoc: {
//     source: ...
//     destination: ...
//   },
//   ...
// }
const tasks = Array.from(sourcesMap).flatMap(([type, definition]) => {
  // base tasks

  // deletes all files at destination except for files named in keep,
  // by default that's only index.adoc, index.adoc is not symlinked
  // so we don't want to remove it
  const clean = (destination, keep) => {
    const deleteAry = [`${destination}/*`] // files in destination needs to be deleted
    // append any exceptions, i.e. files to keep at the destination
    deleteAry.push(...(keep || ['index.adoc']).map((file) => `!${destination}/${file}`))
    return deleteAsync(deleteAry, {
      force: true,
    })
  }

  // creates symlinks from source to destination that satisfy the
  // given filter removing the basedir from a path, i.e. symlinking
  // from a flat hiearchy
  const createSymlinks = (source, destination, filter) => {
    const filterFn = through2.obj((file, enc, done) => {
      if (filter && !filter(file.contents)) {
        done() // skip
      } else {
        done(null, file) // process
      }
    })

    return gulp.src(source)
      .pipe(filterFn)
      .pipe(
        map((file, done) => {
          // this flattens the output to just .../pages/.../file.ext
          // instead of .../pages/camel-.../src/main/docs/.../file.ext
          file.base = path.dirname(file.path)
          done(null, file)
        })
      )
      .pipe(gulp.symlink(destination, {
        relativeSymlinks: true,
      }))
  }

  // generates sorted & grouped nav.adoc file from a set of .adoc
  // files at the destination
  const createNav = (destination, filter) => {
    const filterFn = through2.obj((file, enc, done) => {
      if (filter && !filter(file.path)) {
        done() // skip
      } else {
        done(null, file) // process
      }
    })

    return gulp.src(`${type}-nav.adoc.template`)
      .pipe(insertGeneratedNotice())
      .pipe(
        inject(
          gulp.src([
            `${destination}/**/*.adoc`,
            `!${destination}/index.adoc`,
          ])
          .pipe(filterFn)
          .pipe(sort(compare)),
          {
            removeTags: true,
            transform: (filename, file) => {
              const filepath = path.basename(filename)
              const title = titleFrom(file)
              if (groupFrom(file) !== null) {
                return `*** xref:${filepath}[${title}]`
              }
              return `** xref:${filepath}[${title}]`
            },
          }
        )
      )
      .pipe(rename('nav.adoc'))
      .pipe(gulp.dest(`${destination}/../`))
  }

  // creates symlinks from source to destination for every example
  // file referenced from .adoc file in the source maintaining the
  // basedir from the file path, i.e. symlinking from a deep hiearchy
  const createExampleSymlinks = (source, destination) => {
    const extractExamples = function (file, enc, done) {
      const asciidoc = file.contents.toString()
      const includes = /(?:include::\{examplesdir\}\/)([^[]+)/g
      let example
      while ((example = includes.exec(asciidoc))) {
        const filePath = example[1]
        // filePath points from the root of the repository, our CWD
        // is within `/docs`, so to get the correct path we need to
        // resolve against `..` (`/docs/..`), i.e. root of the
        // repository
        const resolved = path.resolve(path.join('..', example[1]))
        const file = new File({
          path: filePath,
          dirname: path.dirname(resolved),
          base: path.dirname(resolved),
        })
        // replace the .adoc file in the Gulp stream with any example
        // file found in the .adoc file
        this.push(file)
      }

      return done()
    }

    return gulp.src(source) // asciidoc files
      .pipe(through2.obj(extractExamples)) // extracted example files
      // symlink links from a fixed directory, i.e. we could link to
      // the example files from `destination`, that would not work for
      // us as same-named files would overwrite each other, and also
      // the `:include::` for the example includes the full path. So
      // we need a dynamic destination, which we generate based on the
      // `destination` plus directory of the example as without the
      // path leading to the git root. For example, for:
      //  - destination='components/modules/ROOT/examples/'
      //  - file.dirname='/path/to/git/repository/components/example-component/src/test/resources/example-route.xml'
      // we want the resulting destination to be:
      // components/modules/ROOT/examples/components/example-component/src/test/resources/example-route.xml
      .pipe(gulp.symlink((file) => path.join(destination, file.dirname.replace(path.resolve('..'), '')), {
        relativeSymlinks: true,
      }))
  }

  const { asciidoc, image, example, json } = definition

  // we this use a pattern to name an anonymous function:
  // const { [name]: f } = { [name]: /* function | lambda */ }
  // After decomposition f is a function where f.name === name,
  // since Function.name is read-only property we cannot assign it
  // and this is an equvalent of:
  // const name = '...'
  // const ignored = {
  //   [name]: /* function | lambda */
  // }
  // allowing us to set Function.name implicitly in the declaration.
  // That is: ignored[name].name === name.
  //
  // This is helpful for Gulp as it gets the name of the task from
  // the name of the function.
  const named = (name, task, ...args) => {
    const { [name]: n } = { [name]: () => task(...args) }
    return n
  }

  // accumulates all tasks performed per _kind_.
  const allTasks = []

  if (asciidoc && asciidoc.source) {
    allTasks.push(
      gulp.series(
        named(`clean:asciidoc:${type}`, clean, asciidoc.destination, asciidoc.keep),
        named(`symlink:asciidoc:${type}`, createSymlinks, asciidoc.source, asciidoc.destination),
        named(`nav:asciidoc:${type}`, createNav, asciidoc.destination)
      )
    )
  }

  if (image) {
    allTasks.push(
      gulp.series(
        named(`clean:image:${type}`, clean, image.destination, image.keep),
        named(`symlink:image:${type}`, createSymlinks, image.source, image.destination)
      )
    )
  }

  if (example) {
    allTasks.push(
      gulp.series(
        named(`clean:example:${type}`, clean, example.destination, ['json', 'js']),
        named(`symlink:example:${type}`, createExampleSymlinks, example.source, example.destination)
      )
    )
  }

  if (json) {
    let tasks = [
      named(`clean:json:${type}`, clean, json.destination, json.keep),
      named(`symlink:json:${type}`, createSymlinks, json.source, json.destination, json.filter)
    ]

    if (asciidoc && !asciidoc.source) {
      tasks.push(
        named(`nav:asciidoc:${type}`, createNav, asciidoc.destination, asciidoc.filter)
      )
    }

    allTasks.push(
      gulp.series(tasks)
    )
  }

  if (allTasks.length > 0) {
    return allTasks
  }

  return []
}, [])

export default gulp.parallel(tasks)
