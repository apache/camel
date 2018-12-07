const gulp = require('gulp')
const { dest, src, symlink } = require('gulp');
const map = require('map-stream')
const path = require('path');
const inject = require('gulp-inject');
const rename = require('gulp-rename');

gulp.task('symlinks', () => {
    return src('../components/*/src/main/docs/*.adoc')
        .pipe(map((file, done) => {
            // this flattens the output to just .../pages/....adoc
            // instead of .../pages/camel-.../src/main/docs/....adoc
            file.base = path.dirname(file.path);
            done(null, file);
        }))
        .pipe(symlink('components/modules/ROOT/pages/', {
            relativeSymlinks: true
        }));
});

gulp.task('nav', () => {
    return src('nav.adoc.template')
        .pipe(inject(src('../components/*/src/main/docs/*.adoc'), {
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
});

gulp.task('default', gulp.series('symlinks', 'nav'));
