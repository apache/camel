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
package org.apache.camel.maven.packaging;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Replacement for the npm/Yarn/Gulp pipeline that previously ran in {@code docs/} to prepare the Antora source tree.
 *
 * <p>
 * This mojo performs three tasks, mirroring the previous {@code docs/gulpfile.js}:
 * <ol>
 * <li>Clean and symlink {@code .adoc}, image and {@code .json} files from {@code components/**}, {@code core/**} and
 * {@code dsl/**} into the Antora source layout under {@code docs/components/modules/...} and
 * {@code core/camel-core-engine/src/main/docs/modules/eips/...}.</li>
 * <li>For each grouping, generate a sorted {@code nav.adoc} from the matched files using their {@code :doctitle:} (or
 * {@code = Title}) and optional {@code :group:} attributes, injected into a per-grouping template under
 * {@code docs/*-nav.adoc.template}.</li>
 * <li>For each linked {@code .adoc}, parse {@code include::&#123;examplesdir&#125;/...} references and create matching
 * symlinks under {@code components/modules/ROOT/examples/...}.</li>
 * </ol>
 *
 * <p>
 * <b>Glob semantics.</b> The {@code sources} table below uses Ant-style patterns matched by the JDK's
 * {@link java.nio.file.PathMatcher#matches(Path) glob:} matcher: {@code *} (within a segment), {@code **} (across
 * segments), {@code ?}, character classes ({@code [a-z]}) and brace alternatives ({@code &#123;a,b&#125;}). The
 * original {@code gulpfile.js} used {@code !(...)} extglob negation; we express the same intent with separate
 * {@code excludes} lists per {@code KindSpec} entry, which the walker post-filters out of the matched set.
 *
 * <p>
 * <b>Symlinks on Windows.</b> {@code Files.createSymbolicLink} requires either Administrator rights or Developer Mode
 * on Windows. When symlink creation fails with a {@code FileSystemException} the mojo falls back to a plain file copy
 * (logged once per build). On macOS / Linux real relative symlinks are always produced; the byte-for-byte output then
 * matches what gulp's {@code vinyl-fs} produced.
 */
@Mojo(name = "prepare-doc-symlinks", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true)
public class PrepareDocSymlinksMojo extends AbstractMojo {

    /**
     * The {@code docs/} module directory. All {@code destination} paths and {@code *-nav.adoc.template} files are
     * resolved relative to this directory; output {@code nav.adoc} files are written next to their destinations.
     */
    @Parameter(defaultValue = "${project.basedir}", required = true)
    private File baseDir;

    /**
     * Repository root used to resolve {@code source} globs. Each {@code source} entry in the hard-coded {@code sources}
     * table is a path relative to {@code baseDir} (typically starting with {@code ../}); after resolution they walk
     * subtrees under {@code rootDir}.
     */
    @Parameter(defaultValue = "${project.basedir}/..", required = true)
    private File rootDir;

    /** The maven project. */
    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;

    /**
     * Skip the entire mojo. Useful on unsupported architectures (was {@code skipOnUnsupported} for the
     * frontend-maven-plugin block).
     */
    @Parameter(property = "camel.skipDocSymlinks", defaultValue = "false")
    private boolean skip;

    private static final Pattern DOC_TITLE = Pattern.compile(":doctitle: (.*)");
    private static final Pattern HEADING = Pattern.compile("[=#] (.*)");
    private static final Pattern GROUP = Pattern.compile(":group: (.*)");
    private static final Pattern EXAMPLES_INCLUDE = Pattern.compile("include::\\{examplesdir\\}/([^\\[]+)");

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Whether we already logged the Windows symlink-fallback warning. */
    private boolean copyFallbackWarned;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping prepare-doc-symlinks");
            return;
        }

        Path base = baseDir.toPath().toAbsolutePath().normalize();
        Path root = rootDir.toPath().toAbsolutePath().normalize();

        try {
            for (DocGroup group : sources()) {
                getLog().info("prepare-doc-symlinks: " + group.type);
                runGroup(base, root, group);
            }
        } catch (IOException e) {
            throw new MojoFailureException("Failed to prepare doc symlinks", e);
        }
    }

    private void runGroup(Path base, Path root, DocGroup group) throws IOException, MojoFailureException {
        // Both includes and destinations are repo-root-relative; resolve against root for consistency.
        if (group.asciidoc != null && !group.asciidoc.includes().isEmpty()) {
            Path dest = root.resolve(group.asciidoc.destination()).normalize();
            clean(dest, group.asciidoc.keep());
            createSymlinks(root, group.asciidoc, dest);
            createNav(base, group.type, dest, group.asciidoc.pathFilter());
        }
        if (group.image != null) {
            Path dest = root.resolve(group.image.destination()).normalize();
            clean(dest, group.image.keep());
            createSymlinks(root, group.image, dest);
        }
        if (group.example != null) {
            Path dest = root.resolve(group.example.destination()).normalize();
            clean(dest, group.example.keep());
            createExampleSymlinks(root, group.example, dest);
        }
        if (group.json != null) {
            Path dest = root.resolve(group.json.destination()).normalize();
            clean(dest, group.json.keep());
            createSymlinks(root, group.json, dest);
            // gulp also generates a nav for the eips group whose asciidoc spec has no source (only filter + dest).
            if (group.asciidoc != null && group.asciidoc.includes().isEmpty()) {
                Path adocDest = root.resolve(group.asciidoc.destination()).normalize();
                createNav(base, group.type, adocDest, group.asciidoc.pathFilter());
            }
        }
    }

    // ----- clean ----------------------------------------------------------------------------------------------------

    private void clean(Path destination, List<String> keep) throws IOException {
        if (!Files.isDirectory(destination)) {
            Files.createDirectories(destination);
            return;
        }
        Set<String> keepNames = keep == null || keep.isEmpty() ? Set.of("index.adoc") : new LinkedHashSet<>(keep);
        try (Stream<Path> stream = Files.list(destination)) {
            for (Path child : stream.toList()) {
                if (keepNames.contains(child.getFileName().toString())) {
                    continue;
                }
                deleteRecursive(child);
            }
        }
    }

    private static void deleteRecursive(Path p) throws IOException {
        // Use NOFOLLOW_LINKS so we delete the symlink itself, never its target.
        if (Files.isSymbolicLink(p) || !Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
            Files.deleteIfExists(p);
            return;
        }
        try (Stream<Path> walk = Files.walk(p)) {
            walk.sorted(Comparator.reverseOrder()).forEach(child -> {
                try {
                    Files.deleteIfExists(child);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    // ----- symlinks (flat layout) -----------------------------------------------------------------------------------

    /**
     * Walk the spec's includes/excludes under {@code root}, optionally JSON-filter, then create one relative symlink
     * per match at {@code destination/<basename>}.
     *
     * <p>
     * If two distinct sources flatten to the same {@code destination/<basename>} they are passed through
     * {@link #disambiguate(Path, List)} so the build fails loudly rather than silently overwriting the first symlink
     * with the second (the bug behind CAMEL-23531).
     */
    private void createSymlinks(Path root, KindSpec spec, Path destination) throws IOException, MojoFailureException {
        Files.createDirectories(destination);
        String jsonRootKey = spec.jsonRootKey();

        // Group matches by their default destination basename so we can detect and resolve collisions
        // before any symlink is written.
        Map<String, List<Path>> bySrcName = new LinkedHashMap<>();
        for (Path src : walk(root, spec.includes(), spec.excludes())) {
            if (jsonRootKey != null && !matchesJsonFilter(src, jsonRootKey)) {
                continue;
            }
            bySrcName.computeIfAbsent(src.getFileName().toString(), k -> new ArrayList<>()).add(src);
        }

        Set<Path> uniqueLinks = new LinkedHashSet<>();
        int sources = 0;
        for (Map.Entry<String, List<Path>> entry : bySrcName.entrySet()) {
            List<Path> srcs = entry.getValue();
            List<String> linkNames = srcs.size() == 1
                    ? List.of(entry.getKey())
                    : disambiguate(destination, srcs);
            for (int i = 0; i < srcs.size(); i++) {
                Path link = destination.resolve(linkNames.get(i));
                createRelativeSymlink(link, srcs.get(i));
                uniqueLinks.add(link);
                sources++;
            }
        }
        getLog().info("  → linked " + countSuffix(uniqueLinks.size(), sources, "files") + " to "
                      + relativize(destination));
    }

    /**
     * Resolve a basename collision by inserting a per-source discriminator before the file extension.
     *
     * <p>
     * The only known collisions are the Jackson 2.x / 3.x dataformat pairs (CAMEL-23531): both lines register the same
     * dataformat {@code name} for DSL drop-in compatibility, so their generated descriptors share a basename (e.g.
     * {@code jackson.json}). We disambiguate by appending {@code 2} or {@code 3} based on the artifact directory the
     * source lives in (see {@link PackageDataFormatMojo#jacksonFamilySuffix(String)}).
     *
     * <p>
     * Any future collision that doesn't match this rule fails the build rather than silently overwriting one of the
     * sources.
     */
    static List<String> disambiguate(Path destination, List<Path> srcs) throws MojoFailureException {
        String baseName = srcs.get(0).getFileName().toString();
        int dot = baseName.lastIndexOf('.');
        String stem = dot < 0 ? baseName : baseName.substring(0, dot);
        String ext = dot < 0 ? "" : baseName.substring(dot);

        List<String> names = new ArrayList<>(srcs.size());
        Set<String> seen = new LinkedHashSet<>();
        for (Path src : srcs) {
            String artifact = artifactDirName(src);
            if (artifact == null || !artifact.contains("jackson")) {
                throw new MojoFailureException(collisionMessage(destination, baseName, srcs));
            }
            String name = stem + PackageDataFormatMojo.jacksonFamilySuffix(artifact) + ext;
            if (!seen.add(name)) {
                throw new MojoFailureException(collisionMessage(destination, baseName, srcs));
            }
            names.add(name);
        }
        return names;
    }

    /**
     * Returns the artifact directory name (the segment immediately under {@code components/}) for the given source
     * path, or {@code null} if the path doesn't live under {@code components/}.
     */
    private static String artifactDirName(Path src) {
        for (int i = 0; i < src.getNameCount() - 1; i++) {
            if ("components".equals(src.getName(i).toString())) {
                return src.getName(i + 1).toString();
            }
        }
        return null;
    }

    private static String collisionMessage(Path destination, String baseName, List<Path> srcs) {
        StringBuilder sb = new StringBuilder("Doc symlink collision: ").append(srcs.size())
                .append(" sources flatten to ").append(destination).append('/').append(baseName).append(":\n");
        for (Path s : srcs) {
            sb.append("  - ").append(s).append('\n');
        }
        sb.append("Resolve by giving the sources distinct basenames or by extending "
                  + "PrepareDocSymlinksMojo.disambiguate() with a rule that produces unique destination names.");
        return sb.toString();
    }

    // ----- example symlinks (preserves repo-relative directory hierarchy) -------------------------------------------

    private void createExampleSymlinks(Path root, KindSpec spec, Path destination) throws IOException {
        Files.createDirectories(destination);
        // Dedup: two .adoc files may reference the same `include::{examplesdir}/...` target, but only one symlink
        // ends up on disk. Count unique links so the log matches what `find -type l` would report.
        Set<Path> uniqueLinks = new LinkedHashSet<>();
        int occurrences = 0;
        for (Path adoc : walk(root, spec.includes(), spec.excludes())) {
            String content = Files.readString(adoc, StandardCharsets.UTF_8);
            Matcher m = EXAMPLES_INCLUDE.matcher(content);
            while (m.find()) {
                String includePath = m.group(1);
                Path resolved = root.resolve(includePath).normalize();
                Path relDir = root.relativize(resolved.getParent());
                Path linkDir = destination.resolve(relDir.toString());
                Files.createDirectories(linkDir);
                Path link = linkDir.resolve(resolved.getFileName().toString());
                if (uniqueLinks.add(link)) {
                    createRelativeSymlink(link, resolved);
                }
                occurrences++;
            }
        }
        getLog().info("  → linked " + countSuffix(uniqueLinks.size(), occurrences, "example files")
                      + " to " + relativize(destination));
    }

    /** Format a count like {@code "7 files"} or {@code "7 files (from 9 sources)"} when occurrences differ. */
    private static String countSuffix(int unique, int occurrences, String noun) {
        if (occurrences == unique) {
            return unique + " " + noun;
        }
        return unique + " " + noun + " (from " + occurrences + " sources)";
    }

    private void createRelativeSymlink(Path link, Path target) throws IOException {
        if (Files.exists(link, LinkOption.NOFOLLOW_LINKS)) {
            Files.delete(link);
        }
        Path relative = link.getParent().relativize(target);
        try {
            Files.createSymbolicLink(link, relative);
        } catch (FileSystemException fse) {
            // Windows without Developer Mode / Admin → fall back to a plain copy. Matches vinyl-fs behavior.
            if (!copyFallbackWarned) {
                getLog().warn("⚠️ Cannot create symbolic links on this platform; falling back to file copies. "
                              + "On Windows, enable Developer Mode or run as Administrator to get real symlinks. "
                              + "First failure: " + fse.getMessage());
                copyFallbackWarned = true;
            }
            Files.copy(target, link, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private boolean matchesJsonFilter(Path file, String rootKey) {
        try {
            JsonNode tree = JSON.readTree(file.toFile());
            if ("eip".equals(rootKey)) {
                JsonNode model = tree.path("model");
                if (model.isMissingNode() || model.isNull()) {
                    return false;
                }
                JsonNode label = model.path("label");
                return label.isTextual() && label.asText().contains("eip");
            }
            return tree.has(rootKey);
        } catch (IOException e) {
            // gulp simply skipped unparseable / unreadable files; do the same, but log at warn
            // so a corrupt component JSON is at least diagnosable from output.
            getLog().warn("⚠️ Skipping JSON file (unreadable or unparseable): " + file + " (" + e.getMessage() + ")");
            return false;
        }
    }

    // ----- nav generation -------------------------------------------------------------------------------------------

    private void createNav(Path base, String type, Path destination, Predicate<String> pathFilter) throws IOException {
        Path template = base.resolve(type + "-nav.adoc.template");
        if (!Files.isRegularFile(template)) {
            return;
        }
        // Flat scan is intentional: every entry in `destination` is a symlink produced by createSymlinks
        // which flattens to `<destination>/<basename>`. gulpfile.js used a recursive `**/*.adoc` glob, but
        // there are no subdirectories at this layer, so a flat Files.list() is equivalent.
        List<Path> entries = new ArrayList<>();
        if (Files.isDirectory(destination)) {
            try (Stream<Path> s = Files.list(destination)) {
                for (Path p : s.toList()) {
                    String name = p.getFileName().toString();
                    if (!name.endsWith(".adoc")) {
                        continue;
                    }
                    if ("index.adoc".equals(name)) {
                        continue;
                    }
                    if (pathFilter != null && !pathFilter.test(p.toString())) {
                        continue;
                    }
                    entries.add(p);
                }
            }
        }

        // Resolve metadata (title/group) once per file, following symlinks. Match the gulpfile's
        // titleFrom/groupFrom semantics: warn-and-skip on unreadable target or empty content rather
        // than failing the whole build, so a single broken file doesn't take the nav down.
        Map<Path, String> titles = new LinkedHashMap<>();
        Map<Path, String> groups = new LinkedHashMap<>();
        List<Path> usableEntries = new ArrayList<>(entries.size());
        for (Path p : entries) {
            String content;
            try {
                content = readFollowingSymlink(p);
            } catch (IOException e) {
                getLog().warn("⚠️ Failed to read symlink target: " + p + " (" + e.getMessage() + ")");
                continue;
            }
            if (content.isBlank()) {
                getLog().warn("⚠️ No content found for file: " + p);
                continue;
            }
            String title = extractTitle(p, content);
            titles.put(p, title);
            Matcher g = GROUP.matcher(content);
            if (g.find()) {
                groups.put(p, g.group(1));
            }
            usableEntries.add(p);
        }
        entries = usableEntries;

        entries.sort(new NavComparator(titles, groups));

        // Read templates as UTF-8 to match gulp's explicit Buffer.toString('utf8'); important on Windows where
        // the JVM default charset may be CP1252 and would corrupt any non-ASCII content on round-trip.
        // Files.readString(Path) is documented UTF-8 already (JDK 11+); we pass the charset explicitly so the
        // intent is obvious to reviewers and survives any future API drift.
        String generated = Files.readString(base.resolve("generated.txt"), StandardCharsets.UTF_8);
        String tpl = Files.readString(template, StandardCharsets.UTF_8);

        StringBuilder navLines = new StringBuilder();
        for (Path p : entries) {
            String prefix = groups.containsKey(p) ? "*** " : "** ";
            navLines.append(prefix).append("xref:").append(p.getFileName()).append('[').append(titles.get(p))
                    .append(']').append('\n');
        }

        // generated.txt already ends with '\n'; gulp-inject leaves an additional blank line between the inserted
        // body and the next static template line, so we append one more newline here.
        String generatedBlock = generated.endsWith("\n") ? generated + "\n" : generated + "\n\n";
        String rendered = replaceBlock(tpl, "<!-- generated:txt -->", "<!-- endinject -->", generatedBlock);
        rendered = replaceBlock(rendered, "<!-- inject:adoc -->", "<!-- endinject -->", navLines.toString());

        Path navOut = destination.getParent().resolve("nav.adoc");
        Files.writeString(navOut, rendered, StandardCharsets.UTF_8);
        getLog().info("  → generated " + relativize(navOut) + " with " + entries.size() + " entries");
    }

    /**
     * Replace the block delimited by {@code openMarker} and {@code endMarker} (each on its own line) with the given
     * {@code content}. Both marker lines (and their trailing newlines) are stripped — mimicking gulp-inject's
     * {@code removeTags: true} mode. The caller is responsible for terminating {@code content} with whatever trailing
     * newlines are required.
     */
    static String replaceBlock(String template, String openMarker, String endMarker, String content) {
        int open = template.indexOf(openMarker);
        if (open < 0) {
            return template;
        }
        int lineStart = template.lastIndexOf('\n', open) + 1; // 0 if marker is on first line
        int end = template.indexOf(endMarker, open);
        if (end < 0) {
            return template;
        }
        int afterEndNewline = template.indexOf('\n', end);
        int repEnd = afterEndNewline < 0 ? template.length() : afterEndNewline + 1;
        return template.substring(0, lineStart) + content + template.substring(repEnd);
    }

    private static String readFollowingSymlink(Path p) throws IOException {
        // Files.readString follows symlinks by default; explicit UTF-8 to match gulp and avoid CP1252 surprises
        // on Windows.
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    static String extractTitle(Path file, String content) {
        Matcher d = DOC_TITLE.matcher(content);
        if (d.find()) {
            return d.group(1);
        }
        Matcher h = HEADING.matcher(content);
        if (h.find()) {
            return h.group(1);
        }
        throw new IllegalStateException(file + " contains no :doctitle: nor '= Title' heading");
    }

    /** Comparator matching {@code docs/gulpfile.js} {@code compare()} exactly. */
    static final class NavComparator implements Comparator<Path> {
        private final Map<Path, String> titles;
        private final Map<Path, String> groups;

        NavComparator(Map<Path, String> titles, Map<Path, String> groups) {
            this.titles = titles;
            this.groups = groups;
        }

        @Override
        public int compare(Path f1, Path f2) {
            if (f1.equals(f2)) {
                return 0;
            }
            String g1 = groups.get(f1);
            String g2 = groups.get(f2);
            String t1 = titles.get(f1).toUpperCase(Locale.ROOT);
            String t2 = titles.get(f2).toUpperCase(Locale.ROOT);
            String gu1 = g1 == null ? null : g1.toUpperCase(Locale.ROOT);
            String gu2 = g2 == null ? null : g2.toUpperCase(Locale.ROOT);

            int primary;
            if (gu1 == null && gu2 == null) {
                primary = t1.compareTo(t2);
            } else if (gu1 == null) {
                if (t1.equals(gu2)) {
                    primary = -1;
                } else {
                    primary = t1.compareTo(gu2);
                }
            } else if (gu2 == null) {
                if (t2.equals(gu1)) {
                    primary = 1;
                } else {
                    primary = gu1.compareTo(t2);
                }
            } else if (gu1.equals(gu2)) {
                primary = t1.compareTo(t2);
            } else {
                primary = gu1.compareTo(gu2);
            }
            if (primary != 0) {
                return Integer.signum(primary);
            }
            // TimSort tiebreaker: Java's List.sort requires a totally-ordered comparator (sgn(a,b) = -sgn(b,a) and
            // transitive) and throws IllegalArgumentException otherwise. gulp's compare() never returns 0 for
            // distinct files and could violate transitivity when two entries share the same (group, title); we
            // fall back to filename here so the ordering is total and stable. In practice all current docs have
            // distinct (group, title), so the byte-equivalence diff is unaffected.
            return f1.getFileName().toString().compareTo(f2.getFileName().toString());
        }
    }

    // ----- glob walking ---------------------------------------------------------------------------------------------

    /**
     * Subtrees pruned during every walk — large build artefact / dev dirs we never want to consider. Each entry matches
     * either the exact directory name or a hyphenated sibling ({@code <name>-...}), so {@code .camel-jbang-work} is
     * pruned alongside {@code .camel-jbang} — matching the {@code .camel-jbang*} glob used by gulpfile.js.
     */
    private static final List<String> PRUNED_DIR_PREFIXES = List.of("target", ".camel-jbang");

    /**
     * Walk every file under {@code root} matching at least one of the Ant-style {@code includes} and none of
     * {@code excludes}. Uses {@link FileSystem#getPathMatcher(String) glob:} matchers — supports {@code *}, {@code **},
     * {@code ?}, {@code &#123;a,b&#125;} and character classes (JDK Javadoc on {@code getPathMatcher}).
     *
     * <p>
     * {@code target/} and {@code .camel-jbang/} subtrees are skipped before descent (via
     * {@link FileVisitResult#SKIP_SUBTREE}) so a {@code components/{*}/target/...} pattern can never escape the pruner.
     */
    private List<Path> walk(Path root, List<String> includes, List<String> excludes) throws IOException {
        FileSystem fs = root.getFileSystem();
        List<PathMatcher> includeMatchers = includes.stream().map(p -> fs.getPathMatcher("glob:" + p)).toList();
        List<PathMatcher> excludeMatchers = excludes.stream().map(p -> fs.getPathMatcher("glob:" + p)).toList();
        List<Path> matches = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                for (String prefix : PRUNED_DIR_PREFIXES) {
                    if (name.equals(prefix) || name.startsWith(prefix + "-")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                Path rel = root.relativize(file);
                if (matches(rel, includeMatchers) && !matches(rel, excludeMatchers)) {
                    matches.add(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                // ephemeral entries can vanish mid-walk (e.g. tests writing into .camel-jbang/work); skip them
                return FileVisitResult.CONTINUE;
            }
        });
        matches.sort(Comparator.naturalOrder());
        return matches;
    }

    private static boolean matches(Path rel, List<PathMatcher> matchers) {
        for (PathMatcher m : matchers) {
            if (m.matches(rel)) {
                return true;
            }
        }
        return false;
    }

    // ----- source table ---------------------------------------------------------------------------------------------

    private List<DocGroup> sources() {
        // Each group's component-tree depth range mirrors gulpfile.js verbatim. JDK PathMatcher accepts
        // brace alternatives containing '/' so `{*,*/*}` and `{*,*/*,*/*/*}` collapse the depth lists into
        // a single literal. DSL patterns stay enumerated because gulp pinned DSL to a single depth.
        List<String> componentDocAdoc = List.of(
                "core/camel-base/src/main/docs/*.adoc",
                "core/camel-main/src/main/docs/*.adoc",
                "components/{*,*/*,*/*/*}/src/main/docs/*.adoc");
        List<String> nonComponentSuffixExcludes = List.of(
                "**/*-component.adoc", "**/*-language.adoc", "**/*-dataformat.adoc", "**/*-summary.adoc");
        List<String> componentJsonIncludes = List.of(
                "components/{*,*/*,*/*/*}/src/generated/resources/META-INF/org/apache/camel/{,**/}*.json");

        List<DocGroup> list = new ArrayList<>();

        DocGroup components = new DocGroup("components");
        components.asciidoc = new KindSpec(
                List.of("core/camel-base/src/main/docs/*-component.adoc",
                        "components/{*,*/*,*/*/*}/src/main/docs/*-component.adoc",
                        "components/{*,*/*}/src/main/docs/*-summary.adoc"),
                null, "docs/components/modules/ROOT/pages", null, null, null);
        components.image = new KindSpec(
                List.of("components/{*,*/*}/src/main/docs/*.png"),
                null, "docs/components/modules/ROOT/images", null, null, null);
        components.example = new KindSpec(
                componentDocAdoc, null,
                "docs/components/modules/ROOT/examples", List.of("json", "js"), null, null);
        components.json = new KindSpec(
                componentJsonIncludes, null,
                "docs/components/modules/ROOT/examples/json", null, "component", null);
        list.add(components);

        DocGroup dataformats = new DocGroup("dataformats");
        dataformats.asciidoc = new KindSpec(
                List.of("components/{*,*/*}/src/main/docs/*-dataformat.adoc"),
                null, "docs/components/modules/dataformats/pages", null, null, null);
        dataformats.json = new KindSpec(
                concat(componentJsonIncludes, List.of(
                        "core/camel-core-model/src/generated/resources/META-INF/org/apache/camel/model/dataformat/*.json")),
                null, "docs/components/modules/dataformats/examples/json", null, "dataformat", null);
        list.add(dataformats);

        DocGroup languages = new DocGroup("languages");
        languages.asciidoc = new KindSpec(
                List.of("components/{*,*/*}/src/main/docs/*-language.adoc",
                        "core/camel-core-languages/src/main/docs/modules/languages/pages/*-language.adoc"),
                null, "docs/components/modules/languages/pages", null, null, null);
        languages.json = new KindSpec(
                List.of("components/{*,*/*,*/*/*}/src/generated/resources/META-INF/org/apache/camel/*/{,**/}*.json",
                        "core/camel-core-languages/src/generated/resources/META-INF/org/apache/camel/language/{,**/}*.json",
                        "core/camel-core-model/src/generated/resources/META-INF/org/apache/camel/model/language/*.json"),
                null, "docs/components/modules/languages/examples/json", null, "language", null);
        list.add(languages);

        DocGroup others = new DocGroup("others");
        others.asciidoc = new KindSpec(
                // gulpfile.js — `components/{*,*/*}` (depth 1 or 2) and dsl at depths 0, 1 and 2
                // (`dsl/src/main/docs`, `dsl/*/src/main/docs`, `dsl/*/*/src/main/docs`). target/.camel-jbang
                // subtrees are pruned by the walker.
                List.of("core/camel-base/src/main/docs/*.adoc",
                        "core/camel-main/src/main/docs/*.adoc",
                        "components/{*,*/*}/src/main/docs/*.adoc",
                        "dsl/src/main/docs/*.adoc",
                        "dsl/*/src/main/docs/*.adoc",
                        "dsl/*/*/src/main/docs/*.adoc"),
                nonComponentSuffixExcludes,
                "docs/components/modules/others/pages",
                List.of("index.adoc", "reactive-threadpoolfactory-vertx.adoc"), null, null);
        others.json = new KindSpec(
                // gulp used `components/{*,*/*,*/*/*}/src/generated/resources/*.json` — only top-level .json
                // files (i.e. those NOT under META-INF/...) qualify as "other" component metadata.
                List.of("components/{*,*/*,*/*/*}/src/generated/resources/*.json"),
                null, "docs/components/modules/others/examples/json", null, "other", null);
        list.add(others);

        DocGroup eips = new DocGroup("eips");
        eips.asciidoc = new KindSpec(
                Collections.emptyList(), null,
                "core/camel-core-engine/src/main/docs/modules/eips/pages", null, null,
                path -> !path.endsWith("enterprise-integration-patterns.adoc"));
        eips.json = new KindSpec(
                List.of("core/camel-core-model/src/generated/resources/META-INF/org/apache/camel/model/{,**/}*.json"),
                null, "core/camel-core-engine/src/main/docs/modules/eips/examples/json", null, "eip", null);
        list.add(eips);

        DocGroup faq = new DocGroup("manual:faq");
        faq.example = new KindSpec(
                List.of("docs/user-manual/modules/faq/**/*.adoc"),
                null, "docs/user-manual/modules/faq/examples", List.of("json", "js"), null, null);
        list.add(faq);

        return list;
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> out = new ArrayList<>(a.size() + b.size());
        out.addAll(a);
        out.addAll(b);
        return out;
    }

    private String relativize(Path p) {
        return baseDir.toPath().toAbsolutePath().normalize().relativize(p.toAbsolutePath().normalize()).toString();
    }

    // ----- value objects --------------------------------------------------------------------------------------------

    private static final class DocGroup {
        final String type;
        KindSpec asciidoc;
        KindSpec image;
        KindSpec example;
        KindSpec json;

        DocGroup(String type) {
            this.type = type;
        }
    }

    /**
     * One scan specification — a list of Ant-style {@code includes} plus optional {@code excludes}, a flattening
     * destination, and either a JSON-root-key or path-predicate filter applied after the walk.
     *
     * <p>
     * {@code keep} is the (unused entries of the) clean step's whitelist for {@code destination}. {@code jsonRootKey}
     * is mutually exclusive with {@code pathFilter}: the former is used by symlinking kinds that select component /
     * dataformat / language / EIP JSON; the latter by the EIP {@code .adoc} grouping that filters out the umbrella
     * page.
     */
    private record KindSpec(List<String> includes, List<String> excludes, String destination, List<String> keep,
            String jsonRootKey, Predicate<String> pathFilter) {
        KindSpec {
            // Normalize null lists so callers can blindly iterate / check isEmpty().
            includes = includes == null ? Collections.emptyList() : includes;
            excludes = excludes == null ? Collections.emptyList() : excludes;
        }
    }

}
