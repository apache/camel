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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.DstWorldModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.util.PackageHelper;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.build.BuildContext;

import static org.apache.camel.tooling.util.PackageHelper.findCamelDirectory;

/**
 * Generates the table of the fake worlds in the camel-test-dst documentation (and its copy in the catalog) from the
 * {@code dstWorlds} sections of the component JSON files of the catalog (CAMEL-26438).
 */
@Mojo(name = "update-dst-worlds-doc", threadSafe = true, defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class UpdateDstWorldsDocMojo extends AbstractGeneratorMojo {

    static final String START_TOKEN = "// dst worlds: START";
    static final String END_TOKEN = "// dst worlds: END";
    static final String DOC = "test-dst.adoc";

    @Parameter(defaultValue = "${project.basedir}/src/generated/resources/org/apache/camel/catalog/components")
    protected File componentsDir;

    @Parameter(defaultValue = "${project.basedir}/src/generated/resources/org/apache/camel/catalog/docs")
    protected File docsDir;

    @Parameter(defaultValue = "${project.basedir}/")
    protected File baseDir;

    @Inject
    public UpdateDstWorldsDocMojo(MavenProjectHelper projectHelper, BuildContext buildContext) {
        super(projectHelper, buildContext);
    }

    @Override
    public void execute() throws MojoExecutionException {
        File dstDir = findCamelDirectory(baseDir, "components/camel-test/camel-test-dst");
        if (dstDir == null || !componentsDir.isDirectory()) {
            getLog().debug("No camel-test-dst folder or no catalog components found, skipping execution");
            return;
        }
        try {
            String table = renderTable(findWorlds(componentsDir.toPath()));
            Path doc = dstDir.toPath().resolve("src/main/docs/" + DOC);
            if (updateDoc(doc, table)) {
                getLog().info("Updated the table of DST worlds in " + doc);
            }
            // the catalog copy of the page was made before this goal ran
            Path copy = docsDir.toPath().resolve(DOC);
            if (Files.isRegularFile(copy)) {
                updateDoc(copy, table);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Error updating the table of DST worlds", e);
        }
    }

    /** A world with the component it belongs to. */
    record Row(String artifactId, DstWorldModel world) {
    }

    /** The worlds of the component JSON files in the directory, sorted by artifact id and world name. */
    static List<Row> findWorlds(Path dir) throws IOException {
        List<Row> rows = new ArrayList<>();
        List<Path> files;
        try (Stream<Path> list = Files.list(dir)) {
            files = list.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
        }
        for (Path file : files) {
            String text = Files.readString(file);
            if (!text.contains("\"dstWorlds\"")) {
                continue;
            }
            ComponentModel model = JsonMapper.generateComponentModel(text);
            for (DstWorldModel world : model.getDstWorlds()) {
                rows.add(new Row(model.getArtifactId(), world));
            }
        }
        rows.sort(Comparator.comparing(Row::artifactId).thenComparing(r -> r.world().getName()));
        return rows;
    }

    /** The AsciiDoc table of the worlds. */
    static String renderTable(List<Row> rows) {
        StringJoiner sj = new StringJoiner("\n");
        sj.add("[cols=\"2,3,3,4,2\"]");
        sj.add("|===");
        sj.add("|Component |World (tests) |Fake |What it covers |Issues");
        for (Row row : rows) {
            DstWorldModel w = row.world();
            sj.add("");
            sj.add("|" + row.artifactId());
            String tests = w.getTests().isEmpty() ? "" : " (" + code(w.getTests()) + ")";
            sj.add("|`" + w.getName() + "`" + tests);
            sj.add("|" + escape(w.getFake()));
            if (w.getCovers().isEmpty()) {
                sj.add("|");
            } else {
                sj.add("a|* " + escape(w.getCovers().get(0)));
                for (int i = 1; i < w.getCovers().size(); i++) {
                    sj.add("* " + escape(w.getCovers().get(i)));
                }
            }
            sj.add("|" + String.join(", ", w.getIssues()));
        }
        sj.add("|===");
        return sj.toString();
    }

    private static String code(List<String> names) {
        StringJoiner sj = new StringJoiner(", ");
        names.forEach(n -> sj.add("`" + n + "`"));
        return sj.toString();
    }

    /** A value on one line of its cell. */
    private static String escape(String text) {
        return text.replace("|", "\\|").replaceAll("\\s*[\\r\\n]+\\s*", " ");
    }

    /** Puts the table between the markers of the page; returns whether the page changed. */
    static boolean updateDoc(Path doc, String table) throws IOException, MojoExecutionException {
        String text = PackageHelper.loadText(doc);
        String changed = replaceTable(text, table);
        if (changed == null) {
            throw new MojoExecutionException("No " + START_TOKEN + " / " + END_TOKEN + " markers in " + doc);
        }
        if (changed.equals(text)) {
            return false;
        }
        PackageHelper.writeText(doc.toFile(), changed);
        return true;
    }

    /** The text with the table between the markers, or null if the text has no markers. */
    static String replaceTable(String text, String table) {
        int start = text.indexOf(START_TOKEN);
        int end = text.indexOf(END_TOKEN);
        if (start < 0 || end < start) {
            return null;
        }
        return text.substring(0, start) + START_TOKEN + "\n" + table + "\n" + text.substring(end);
    }
}
