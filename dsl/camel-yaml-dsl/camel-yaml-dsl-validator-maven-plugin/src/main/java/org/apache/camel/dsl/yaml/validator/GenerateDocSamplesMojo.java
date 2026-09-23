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
package org.apache.camel.dsl.yaml.validator;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import com.networknt.schema.Error;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Generates the YAML samples per EIP and per top-level entry that the Camel JBang AI tools return, from the YAML
 * examples in the Camel documentation, and fails the build when an example does not validate.
 * <p>
 * Three sources are read:
 * <ul>
 * <li>every page of the EIP documentation ({@code eipDocsDir}): every route example is a sample of the EIP named after
 * the page ({@code split-eip.adoc} is {@code split}, {@code dead-letter-channel.adoc} is
 * {@code deadLetterChannel})</li>
 * <li>every page of the user manual ({@code userManualDir}) not in {@code excludes} is validated; the pages listed in
 * {@code pages} contribute all of their route examples as samples of the given key ({@code routes} is
 * {@code route})</li>
 * <li>the pages listed in {@code entryPages} ({@code path=key}) contribute the examples that start with the key
 * ({@code yaml-dsl.adoc} is {@code beans})</li>
 * </ul>
 * A route example is a {@code [source,yaml]} block whose first line starts with {@code "- "}; other YAML blocks are
 * fragments and are neither validated nor sampled. The output is a JSON object keyed by the sample name, each value a
 * list of {@code {source, yaml}} objects, written only when its content changed.
 */
@Mojo(name = "generate-doc-samples", threadSafe = true)
public class GenerateDocSamplesMojo extends AbstractMojo {

    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;

    /**
     * Skip generating the samples.
     */
    @Parameter(property = "camel.skipDocSamples", defaultValue = "false")
    private boolean skip;

    /**
     * The directory with the EIP documentation pages.
     */
    @Parameter(defaultValue = "${project.basedir}/../../../core/camel-core-engine/src/main/docs/modules/eips/pages")
    private File eipDocsDir;

    /**
     * The directory with the user manual pages.
     */
    @Parameter(defaultValue = "${project.basedir}/../../../docs/user-manual/modules/ROOT/pages")
    private File userManualDir;

    /**
     * The user manual pages (file name without .adoc) whose route examples are the samples of the given key.
     */
    @Parameter
    private Map<String, String> pages;

    /**
     * Pages given as {@code path=key} (path relative to the project) whose examples starting with {@code - key:} are
     * the samples of the key.
     */
    @Parameter
    private List<String> entryPages;

    /**
     * User manual pages (file names, * and ? wildcards) that are not validated, such as the upgrade guides that show
     * old syntax.
     */
    @Parameter
    private List<String> excludes;

    /**
     * The JSON file to generate.
     */
    @Parameter(
               defaultValue = "${project.basedir}/src/generated/resources/org/apache/camel/dsl/jbang/core/commands/ai/eip-samples.json")
    private File outputFile;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("skipping generating documentation samples as per configuration");
            return;
        }
        if (!eipDocsDir.isDirectory() || !userManualDir.isDirectory()) {
            getLog().warn("Documentation not found (" + eipDocsDir + ", " + userManualDir
                          + "), keeping the existing " + outputFile.getName());
            return;
        }

        YamlValidator validator = new YamlValidator();
        try {
            validator.init();
        } catch (Exception e) {
            throw new MojoExecutionException(e);
        }

        List<String> failures = new ArrayList<>();
        Map<String, List<Sample>> samples;
        try {
            samples = generate(validator, eipDocsDir, userManualDir, pages, entryPages(), excludes, failures);
        } catch (Exception e) {
            throw new MojoExecutionException(e);
        }
        if (!failures.isEmpty()) {
            StringBuilder sb = new StringBuilder("YAML examples in the documentation that do not validate (")
                    .append(failures.size()).append("):\n");
            for (String f : failures) {
                sb.append("\t").append(f).append("\n");
            }
            throw new MojoFailureException(sb.toString());
        }

        int count = samples.values().stream().mapToInt(List::size).sum();
        String json = toJson(samples);
        try {
            if (outputFile.isFile() && json.equals(Files.readString(outputFile.toPath(), StandardCharsets.UTF_8))) {
                getLog().debug("Documentation samples unchanged: " + outputFile);
                return;
            }
            Files.createDirectories(outputFile.toPath().getParent());
            Files.writeString(outputFile.toPath(), json, StandardCharsets.UTF_8);
            getLog().info("Generated " + count + " documentation samples for " + samples.size() + " names: " + outputFile);
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot write " + outputFile, e);
        }
    }

    private Map<File, String> entryPages() throws MojoExecutionException {
        Map<File, String> answer = new TreeMap<>();
        if (entryPages != null) {
            for (String e : entryPages) {
                int pos = e.lastIndexOf('=');
                if (pos <= 0 || pos == e.length() - 1) {
                    throw new MojoExecutionException("entryPages must be path=key: " + e);
                }
                File f = new File(e.substring(0, pos).trim());
                if (!f.isAbsolute()) {
                    f = new File(project.getBasedir(), f.getPath());
                }
                answer.put(f, e.substring(pos + 1).trim());
            }
        }
        return answer;
    }

    /** A sample: the page it comes from and the YAML. */
    record Sample(String source, String yaml) {
    }

    /**
     * Reads, validates and collects the samples; every example that does not validate is added to failures.
     */
    static Map<String, List<Sample>> generate(
            YamlValidator validator, File eipDocsDir, File userManualDir, Map<String, String> pages,
            Map<File, String> entryPages, List<String> excludes, List<String> failures)
            throws Exception {
        Map<String, List<Sample>> samples = new TreeMap<>();

        for (File page : pages(eipDocsDir)) {
            String key = eipKey(page.getName());
            for (String yaml : examples(page)) {
                if (validate(validator, page, yaml, failures)) {
                    samples.computeIfAbsent(key, k -> new ArrayList<>()).add(new Sample(page.getName(), yaml));
                }
            }
        }

        for (File page : pages(userManualDir)) {
            if (excluded(page.getName(), excludes)) {
                continue;
            }
            String key = pages != null ? pages.get(onlyName(page.getName())) : null;
            for (String yaml : examples(page)) {
                if (validate(validator, page, yaml, failures) && key != null) {
                    samples.computeIfAbsent(key, k -> new ArrayList<>()).add(new Sample(page.getName(), yaml));
                }
            }
        }

        for (Map.Entry<File, String> e : entryPages.entrySet()) {
            File page = e.getKey();
            String key = e.getValue();
            if (!page.isFile()) {
                throw new IOException("Page not found: " + page);
            }
            for (String yaml : examples(page)) {
                if (yaml.startsWith("- " + key + ":") && validate(validator, page, yaml, failures)) {
                    samples.computeIfAbsent(key, k -> new ArrayList<>()).add(new Sample(page.getName(), yaml));
                }
            }
        }

        return samples;
    }

    private static List<File> pages(File dir) {
        File[] files = dir.listFiles(f -> f.isFile() && f.getName().endsWith(".adoc"));
        if (files == null) {
            return List.of();
        }
        Arrays.sort(files);
        return List.of(files);
    }

    private static boolean excluded(String name, List<String> excludes) {
        if (excludes != null) {
            for (String pattern : excludes) {
                if (name.matches(glob(pattern.trim()))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A file name pattern with * and ? wildcards as a regular expression. */
    static String glob(String pattern) {
        StringBuilder sb = new StringBuilder();
        for (char ch : pattern.toCharArray()) {
            if (ch == '*') {
                sb.append(".*");
            } else if (ch == '?') {
                sb.append('.');
            } else {
                sb.append(Pattern.quote(String.valueOf(ch)));
            }
        }
        return sb.toString();
    }

    /** The route examples of the page: the YAML blocks that start with a top-level list entry, without callouts. */
    static List<String> examples(File page) throws IOException {
        return DocBlocks.examples(Files.readString(page.toPath(), StandardCharsets.UTF_8));
    }

    private static boolean validate(YamlValidator validator, File page, String yaml, List<String> failures)
            throws Exception {
        List<Error> errors = validator.validate(yaml);
        if (errors.isEmpty()) {
            return true;
        }
        failures.add(page.getName() + ": " + errors.get(0).getMessage() + "\n\t\t"
                     + yaml.stripTrailing().replace("\n", "\n\t\t"));
        return false;
    }

    /** The sample name of an EIP page: split-eip is split, dead-letter-channel is deadLetterChannel. */
    static String eipKey(String fileName) {
        String name = onlyName(fileName);
        if (name.endsWith("-eip")) {
            name = name.substring(0, name.length() - 4);
        }
        StringBuilder sb = new StringBuilder();
        boolean upper = false;
        for (char ch : name.toCharArray()) {
            if (ch == '-') {
                upper = true;
            } else {
                sb.append(upper ? Character.toUpperCase(ch) : ch);
                upper = false;
            }
        }
        return sb.toString();
    }

    private static String onlyName(String fileName) {
        return fileName.endsWith(".adoc") ? fileName.substring(0, fileName.length() - 5) : fileName;
    }

    static String toJson(Map<String, List<Sample>> samples) {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, List<Sample>> e : samples.entrySet()) {
            JsonArray arr = new JsonArray();
            for (Sample s : e.getValue()) {
                JsonObject jo = new JsonObject();
                jo.put("source", s.source());
                jo.put("yaml", s.yaml());
                arr.add(jo);
            }
            root.put(e.getKey(), arr);
        }
        return Jsoner.prettyPrint(Jsoner.serialize(root), 2) + "\n";
    }
}
