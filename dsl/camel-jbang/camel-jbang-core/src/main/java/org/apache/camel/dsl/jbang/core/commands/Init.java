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
package org.apache.camel.dsl.jbang.core.commands;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Stack;
import java.util.StringJoiner;

import org.apache.camel.CamelContext;
import org.apache.camel.dsl.jbang.core.commands.catalog.KameletCatalogHelper;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.ResourceDoesNotExist;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.apache.camel.github.GistResourceResolver;
import org.apache.camel.github.GitHubResourceResolver;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Resource;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.commons.io.IOUtils;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static org.apache.camel.dsl.jbang.core.common.GistHelper.fetchGistUrls;
import static org.apache.camel.dsl.jbang.core.common.GitHubHelper.asGithubSingleUrl;
import static org.apache.camel.dsl.jbang.core.common.GitHubHelper.fetchGithubUrls;

@Command(name = "init", description = "Creates a new Camel integration",
         sortOptions = false, showDefaultValues = true,
         footer = {
                 "%nExamples:",
                 "  camel init hello.java",
                 "  camel init hello.yaml",
                 "  camel init hello.xml",
                 "  camel init --list" })
public class Init extends CamelCommand {

    @Parameters(description = "Name of integration file (or a github link)", arity = "0..1",
                paramLabel = "<file>", parameterConsumer = FileConsumer.class)
    private Path filePath; // Defined only for file path completion; the field never used
    private String file;

    @Option(names = { "--list" }, description = "List available templates")
    private boolean list;

    @Option(names = {
            "--dir",
            "--directory" }, description = "Directory relative path where the new Camel integration will be saved",
            defaultValue = ".")
    private String directory;

    @Option(names = { "--clean-dir", "--clean-directory" },
            description = "Whether to clean directory first (deletes all files in directory)")
    private boolean cleanDirectory;

    @Option(names = { "--from-kamelet" },
            description = "To be used when extending an existing Kamelet")
    private String fromKamelet;

    @Option(names = {
            "--kamelets-version" }, description = "Apache Camel Kamelets version")
    private String kameletsVersion;

    @Option(names = { "--pipe" },
            description = "When creating a yaml file should it be created as a Pipe CR")
    private boolean pipe;

    @Option(names = { "--repo", "--repos" },
            description = "Additional maven repositories (Use commas to separate multiple repositories)")
    protected String repositories;

    public Init(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        if (list) {
            return listTemplates();
        }
        if (file == null) {
            printer().printErr("Missing required parameter: <file>");
            return 1;
        }
        int code = execute();
        if (code == 0) {
            // In case of successful execution, we create the working directory if it does not exist to help the tooling
            // know that it is a Camel JBang project
            createWorkingDirectoryIfAbsent();
        }
        return code;
    }

    private int execute() throws Exception {
        // is the file referring to an existing file on github/gist
        // then we should download the file to local for use
        if (file.startsWith("https://github.com/")) {
            return downloadFromGithub();
        } else if (file.startsWith("https://gist.github.com/")) {
            return downloadFromGist();
        }

        String ext = FileUtil.onlyExt(file, false);
        if ("yaml".equals(ext) && pipe) {
            ext = "init-pipe.yaml";
        }

        if (fromKamelet != null && !"kamelet.yaml".equals(ext)) {
            printer().println("When extending from an existing Kamelet then file must have extension .kamelet.yaml");
            return 1;
        }

        String name = FileUtil.onlyName(file, false);
        InputStream is = null;
        if ("kamelet.yaml".equals(ext)) {
            if (fromKamelet != null) {
                if (kameletsVersion == null) {
                    kameletsVersion = VersionHelper.extractKameletsVersion();
                }
                // load existing kamelet
                is = KameletCatalogHelper.loadKameletYamlSchema(fromKamelet, kameletsVersion, repositories);
            } else if (file.contains("source")) {
                ext = "kamelet-source.yaml";
            } else if (file.contains("sink")) {
                ext = "kamelet-sink.yaml";
            } else {
                ext = "kamelet-action.yaml";
            }
        } else if (ext != null && (ext.startsWith("camel.yaml") || ext.startsWith("camel.xml"))) {
            // we allow xxx.camel.yaml / xxx.camel.xml
            ext = ext.substring(6);
        }

        if (is == null) {
            is = Init.class.getClassLoader().getResourceAsStream("templates/" + ext + ".ftl");
        }
        if (is == null) {
            // fallback to old .tmpl format
            is = Init.class.getClassLoader().getResourceAsStream("templates/" + ext + ".tmpl");
        }
        if (is == null) {
            if (fromKamelet != null) {
                printer().printErr("Existing Kamelet does not exist: " + fromKamelet);
            } else {
                printer().printErr("Unsupported file type: " + ext);
            }
            return 1;
        }
        String content = IOHelper.loadText(is);
        IOHelper.close(is);
        // Strip FreeMarker license header comment (appears as literal text in .ftl files)
        content = content.replaceFirst("(?s)\\A<#--.*?-->\\s*", "");

        if (!directory.equals(".")) {
            if (cleanDirectory) {
                // ensure target dir is created after clean
                CommandHelper.cleanExportDir(directory);
            }
            Path dirPath = Paths.get(directory);
            Files.createDirectories(dirPath);
        }
        Path targetPath = Paths.get(file);
        if (!targetPath.isAbsolute()) {
            targetPath = Paths.get(directory, file);
        }
        content = content.replace("{{ .Name }}", name);
        content = content.replace("[=Name]", name);
        if (fromKamelet != null) {
            content = content.replaceFirst("\\s\\sname:\\s" + fromKamelet, "  name: " + name);
            content = content.replaceFirst("camel.apache.org/provider: \"Apache Software Foundation\"",
                    "camel.apache.org/provider: \"Custom\"");

            StringBuilder sb = new StringBuilder();
            String[] lines = content.split("\n");
            boolean top = true;
            for (String line : lines) {
                // remove top license header
                if (top && line.startsWith("#")) {
                    continue;
                }
                top = false;
                sb.append(line);
                sb.append("\n");
            }
            content = sb.toString();
        }
        if ("java".equals(ext)) {
            String packageDeclaration = computeJavaPackageDeclaration(targetPath);
            content = content.replace("{{ .PackageDeclaration }}", packageDeclaration);
            content = content.replace("[=PackageDeclaration]", packageDeclaration);
        }
        // in case of using relative paths in the file name
        Path parentPath = targetPath.getParent();
        if (parentPath != null) {
            if (".".equals(parentPath.getFileName().toString())) {
                targetPath = Paths.get(file);
            } else {
                Files.createDirectories(parentPath);
            }
        }
        Files.writeString(targetPath, content);
        return 0;
    }

    /**
     * @return The package declaration lines to insert at the beginning of the file or empty string if no package found
     */
    private String computeJavaPackageDeclaration(Path targetPath) throws IOException {
        String packageDeclaration = "";
        String canonicalPath = targetPath.getParent().toRealPath().toString();
        String srcMainJavaPath = Paths.get("src", "main", "java").toString();
        int index = canonicalPath.indexOf(srcMainJavaPath);
        if (index != -1) {
            String packagePath = canonicalPath.substring(index + srcMainJavaPath.length() + 1);
            String packageName = packagePath.replace(java.io.File.separatorChar, '.');
            if (!packageName.isEmpty()) {
                packageDeclaration = "package " + packageName + ";\n\n";
            }
        }
        return packageDeclaration;
    }

    private int listTemplates() {
        // Templates grouped by category with descriptions
        // Only include user-facing templates (not POM/Dockerfile/internal templates)
        Map<String, Map<String, String>> categories = new LinkedHashMap<>();

        Map<String, String> routes = new LinkedHashMap<>();
        routes.put("java", "Java DSL route (MyRoute.java)");
        routes.put("xml", "XML DSL route (my-route.xml)");
        routes.put("yaml", "YAML DSL route (my-route.yaml)");
        categories.put("Routes", routes);

        Map<String, String> kamelets = new LinkedHashMap<>();
        kamelets.put("kamelet-source.yaml", "Kamelet source connector (my-source.kamelet.yaml)");
        kamelets.put("kamelet-sink.yaml", "Kamelet sink connector (my-sink.kamelet.yaml)");
        kamelets.put("kamelet-action.yaml", "Kamelet action processor (my-action.kamelet.yaml)");
        categories.put("Kamelets", kamelets);

        Map<String, String> pipes = new LinkedHashMap<>();
        pipes.put("init-pipe.yaml", "Pipe CR connecting source and sink (my-pipe.yaml --pipe)");
        pipes.put("pipe.yaml", "Pipe resource (my-pipe.pipe.yaml)");
        pipes.put("integration.yaml", "Integration CR (my-integration.integration.yaml)");
        categories.put("Pipes and CRs", pipes);

        Map<String, String> restDsl = new LinkedHashMap<>();
        restDsl.put("rest-dsl.yaml", "REST DSL with OpenAPI (my-api.rest-dsl.yaml)");
        categories.put("REST", restDsl);

        printer().println("Available templates for 'camel init':");
        printer().println();
        for (Map.Entry<String, Map<String, String>> category : categories.entrySet()) {
            printer().println(category.getKey() + ":");
            for (Map.Entry<String, String> template : category.getValue().entrySet()) {
                printer().printf("  %-25s %s%n", template.getKey(), template.getValue());
            }
            printer().println();
        }
        printer().println("Usage: camel init <filename>.<ext>");
        printer().println("The template is selected based on the file extension.");
        printer().println("Example: camel init MyRoute.java");

        return 0;
    }

    private void createWorkingDirectoryIfAbsent() {
        Path work = CommandLineHelper.getWorkDir();
        if (!Files.exists(work)) {
            try {
                Files.createDirectories(work);
            } catch (IOException e) {
                // ignore
            }
        }
    }

    private int downloadFromGithub() throws Exception {
        StringJoiner all = new StringJoiner(",");

        String ext = FileUtil.onlyExt(file);
        boolean wildcard = FileUtil.onlyName(file, false).contains("*");
        if (ext != null && !wildcard) {
            // it is a single file so map to
            String url = asGithubSingleUrl(file);
            all.add(url);
        } else {
            fetchGithubUrls(file, all);
        }

        if (all.length() > 0) {
            // okay we downloaded something so prepare export dir
            if (!directory.equals(".")) {
                Path dirPath = Paths.get(directory);
                if (cleanDirectory) {
                    // ensure target dir is created after clean
                    CommandHelper.cleanExportDir(directory);
                }
                Files.createDirectories(dirPath);
            }

            CamelContext tiny = new DefaultCamelContext();
            try (GitHubResourceResolver resolver = new GitHubResourceResolver()) {
                resolver.setCamelContext(tiny);
                for (String u : all.toString().split(",")) {
                    Resource resource = resolver.resolve(u);
                    if (!resource.exists()) {
                        throw new ResourceDoesNotExist(resource);
                    }
                    String loc = resource.getLocation();
                    String name = FileUtil.stripPath(loc);
                    Path targetPath = Paths.get(directory, name);
                    try (OutputStream os = Files.newOutputStream(targetPath)) {
                        IOUtils.copy(resource.getInputStream(), os);
                    }
                }
            }
        }

        return 0;
    }

    private Integer downloadFromGist() throws Exception {
        StringJoiner all = new StringJoiner(",");

        fetchGistUrls(file, all);

        if (all.length() > 0) {
            // okay we downloaded something so prepare export dir
            if (!directory.equals(".")) {
                Path dirPath = Paths.get(directory);
                if (cleanDirectory) {
                    // ensure target dir is created after clean
                    CommandHelper.cleanExportDir(directory);
                }
                Files.createDirectories(dirPath);
            }

            CamelContext tiny = new DefaultCamelContext();
            try (GistResourceResolver resolver = new GistResourceResolver()) {
                resolver.setCamelContext(tiny);
                for (String u : all.toString().split(",")) {
                    Resource resource = resolver.resolve(u);
                    if (!resource.exists()) {
                        throw new ResourceDoesNotExist(resource);
                    }
                    String loc = resource.getLocation();
                    String name = FileUtil.stripPath(loc);
                    Path targetPath = Paths.get(directory, name);
                    try (OutputStream os = Files.newOutputStream(targetPath)) {
                        IOUtils.copy(resource.getInputStream(), os);
                    }
                }
            }
        }

        return 0;
    }

    static class FileConsumer extends ParameterConsumer<Init> {
        @Override
        protected void doConsumeParameters(Stack<String> args, Init cmd) {
            cmd.file = args.pop();
        }
    }

}
