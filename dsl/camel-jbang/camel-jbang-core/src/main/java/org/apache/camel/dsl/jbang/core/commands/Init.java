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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.StringJoiner;

import javax.lang.model.SourceVersion;

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.apache.camel.CamelContext;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.EnvironmentHelper;
import org.apache.camel.dsl.jbang.core.common.ResourceDoesNotExist;
import org.apache.camel.dsl.jbang.run.catalog.KameletCatalogHelper;
import org.apache.camel.dsl.jbang.run.common.VersionHelper;
import org.apache.camel.github.GistResourceResolver;
import org.apache.camel.github.GitHubResourceResolver;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Resource;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.commons.io.IOUtils;
import org.jline.terminal.Terminal;

import static org.apache.camel.dsl.jbang.run.common.GistHelper.fetchGistUrls;
import static org.apache.camel.dsl.jbang.run.common.GitHubHelper.asGithubSingleUrl;
import static org.apache.camel.dsl.jbang.run.common.GitHubHelper.fetchGithubUrls;

@CommandDefinition(name = "init", description = "Creates a new Camel integration", generateHelp = true,
                   footer = {
                           "Examples:",
                           "  camel init hello.java",
                           "  camel init hello.yaml",
                           "  camel init hello.xml",
                           "  camel init --list" })
public class Init extends CamelCommand {

    private Path filePath; // Defined only for file path completion; the field never used
    @Argument(description = "Name of integration file (or a github link)", paramLabel = "<file>")
    private String file;

    @Option(name = "list", hasValue = false, description = "List available templates")
    private boolean list;

    @Option(name = "dir", aliases = { "directory" },
            description = "Directory relative path where the new Camel integration will be saved", defaultValue = ".")
    private String directory;

    @Option(name = "clean-dir", aliases = { "clean-directory" }, hasValue = false,
            description = "Whether to clean directory first (deletes all files in directory)")
    private boolean cleanDirectory;

    @Option(name = "force", hasValue = false,
            description = "Overwrite a file that exists already (by default the command fails instead)")
    private boolean force;

    @Option(name = "from-kamelet", description = "To be used when extending an existing Kamelet")
    private String fromKamelet;

    @Option(name = "kamelets-version", description = "Apache Camel Kamelets version (auto-detected from classpath if not set)")
    private String kameletsVersion;

    @Option(name = "pipe", hasValue = false, description = "When creating a yaml file should it be created as a Pipe CR")
    private boolean pipe;

    @Option(name = "repo", aliases = { "repos" },
            description = "Additional maven repositories (Use commas to separate multiple repositories)")
    protected String repositories;

    // when the template is picked in the terminal, where to ask whether to overwrite a file that exists already
    private Scanner overwritePrompt;

    public Init(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        if (list) {
            return listTemplates();
        }
        if (file == null) {
            // try interactive picker if running in a TTY and not in CI
            if (EnvironmentHelper.isInteractiveTerminal()) {
                Terminal activeTerminal = EnvironmentHelper.getActiveTerminal();
                return interactivePicker(activeTerminal != null ? activeTerminal.input() : System.in);
            }
            printer().printErr("Missing required parameter: <file>");
            printer().printTip("Run 'camel init --list' to see available templates, or run interactively in a terminal.");
            return CliExitCode.USAGE;
        }
        int code = execute();
        if (code == 0) {
            // In case of successful execution, we create the working directory if it does not exist to help the tooling
            // know that it is a Camel CLI project
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
            printer().printErr("When extending from an existing Kamelet then file must have extension .kamelet.yaml");
            return CliExitCode.USAGE;
        }

        String name = FileUtil.onlyName(file, false);
        if ("java".equals(ext) && !SourceVersion.isName(name)) {
            // the class of the file would not compile (CAMEL-26931: my-route.java, 1bad.java)
            throw CliException.usage(name + " is not a Java class name: name the file like a class, such as MyRoute.java");
        }
        // the kind of a Kamelet from its file name, not the path: src/main/resources/kamelets/my-filter.kamelet.yaml
        // became a source, as resources contains source (CAMEL-26931)
        String fileName = FileUtil.stripPath(file);
        InputStream is = null;
        if ("kamelet.yaml".equals(ext)) {
            if (fromKamelet != null) {
                if (kameletsVersion == null) {
                    kameletsVersion = VersionHelper.extractKameletsVersion();
                }
                // load existing kamelet
                is = KameletCatalogHelper.loadKameletYamlSchema(fromKamelet, kameletsVersion, repositories);
            } else if (fileName.contains("source")) {
                ext = "kamelet-source.yaml";
            } else if (fileName.contains("sink")) {
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
                return CliExitCode.NOT_FOUND;
            } else {
                printer().printErr("Unsupported file type: " + ext);
                return CliExitCode.USAGE;
            }
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
        // in the directory, also for a file given as ./x.yaml (CAMEL-26931: it was written in the current directory)
        Path targetPath = Paths.get(file);
        if (!targetPath.isAbsolute()) {
            targetPath = Paths.get(directory, file).normalize();
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
            Files.createDirectories(parentPath);
        }
        if (existsAlready(targetPath)) {
            return CliExitCode.FAILURE;
        }
        Files.writeString(targetPath, content);
        return 0;
    }

    /**
     * Whether the file is there already and not to be overwritten (without --force), which is then an error. When the
     * template is picked in the terminal, the user is asked there whether to overwrite the file.
     */
    private boolean existsAlready(Path targetPath) {
        if (force || !Files.exists(targetPath)) {
            return false;
        }
        if (overwritePrompt != null) {
            printer().printf("File already exists: %s, overwrite it? [y/N]: ", targetPath);
            String answer = overwritePrompt.hasNextLine() ? overwritePrompt.nextLine().trim() : "";
            if ("y".equalsIgnoreCase(answer) || "yes".equalsIgnoreCase(answer)) {
                return false;
            }
        }
        printer().printErr("File already exists: " + targetPath + " (use --force to overwrite it)");
        return true;
    }

    /**
     * Writes the downloaded files, and none of them when one of them is there already (without --force).
     */
    private int writeDownloaded(List<Resource> resources) throws Exception {
        for (Resource resource : resources) {
            if (existsAlready(Paths.get(directory, FileUtil.stripPath(resource.getLocation())))) {
                return CliExitCode.FAILURE;
            }
        }
        for (Resource resource : resources) {
            Path targetPath = Paths.get(directory, FileUtil.stripPath(resource.getLocation()));
            try (OutputStream os = Files.newOutputStream(targetPath)) {
                IOUtils.copy(resource.getInputStream(), os);
            }
        }
        return 0;
    }

    /**
     * @return The package declaration lines to insert at the beginning of the file or empty string if no package found
     */
    private String computeJavaPackageDeclaration(Path targetPath) {
        // the directories after src/main/java (as whole names: not src/main/javafx, and none for src/main/java itself)
        List<String> names = new ArrayList<>();
        for (Path name : targetPath.toAbsolutePath().normalize().getParent()) {
            names.add(name.toString());
        }
        for (int i = names.size() - 3; i >= 0; i--) {
            if ("src".equals(names.get(i)) && "main".equals(names.get(i + 1)) && "java".equals(names.get(i + 2))) {
                String packageName = String.join(".", names.subList(i + 3, names.size()));
                return packageName.isEmpty() ? "" : "package " + packageName + ";\n\n";
            }
        }
        return "";
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

    int interactivePicker(InputStream input) throws Exception {
        // Build template categories
        Map<String, List<String[]>> categories = new LinkedHashMap<>();
        categories.put("Routes", List.of(
                new String[] { "yaml", "YAML DSL route", ".yaml" },
                new String[] { "java", "Java DSL route", ".java" },
                new String[] { "xml", "XML DSL route", ".xml" }));
        categories.put("Kamelets", List.of(
                new String[] { "kamelet-source.yaml", "Kamelet source connector", ".kamelet.yaml" },
                new String[] { "kamelet-sink.yaml", "Kamelet sink connector", ".kamelet.yaml" },
                new String[] { "kamelet-action.yaml", "Kamelet action processor", ".kamelet.yaml" }));
        List<String[]> pipeTemplates = new ArrayList<>();
        pipeTemplates.add(new String[] { "init-pipe.yaml", "Pipe CR (source to sink)", ".yaml" });
        categories.put("Pipes and CRs", pipeTemplates);

        Scanner scanner = new Scanner(input);

        // Step 1: Pick a category
        printer().println("Select a template category:");
        List<String> categoryNames = new ArrayList<>(categories.keySet());
        for (int i = 0; i < categoryNames.size(); i++) {
            printer().printf("  %d) %s%n", i + 1, categoryNames.get(i));
        }
        printer().print("Choice [1]: ");
        String categoryInput = scanner.nextLine().trim();
        int categoryIdx;
        try {
            categoryIdx = categoryInput.isEmpty() ? 0 : Integer.parseInt(categoryInput) - 1;
        } catch (NumberFormatException e) {
            printer().printErr("Invalid choice: " + categoryInput);
            return 1;
        }
        if (categoryIdx < 0 || categoryIdx >= categoryNames.size()) {
            printer().printErr("Invalid choice: must be between 1 and " + categoryNames.size());
            return 1;
        }

        // Step 2: Pick a template
        String selectedCategory = categoryNames.get(categoryIdx);
        List<String[]> templates = categories.get(selectedCategory);
        printer().println();
        printer().println("Select a template:");
        for (int i = 0; i < templates.size(); i++) {
            printer().printf("  %d) %s%n", i + 1, templates.get(i)[1]);
        }
        printer().print("Choice [1]: ");
        String templateInput = scanner.nextLine().trim();
        int templateIdx;
        try {
            templateIdx = templateInput.isEmpty() ? 0 : Integer.parseInt(templateInput) - 1;
        } catch (NumberFormatException e) {
            printer().printErr("Invalid choice: " + templateInput);
            return 1;
        }
        if (templateIdx < 0 || templateIdx >= templates.size()) {
            printer().printErr("Invalid choice: must be between 1 and " + templates.size());
            return 1;
        }

        String[] selected = templates.get(templateIdx);
        String ext = selected[2];
        String defaultName = "MyRoute" + ext;
        if (ext.endsWith(".kamelet.yaml")) {
            if (selected[0].contains("source")) {
                defaultName = "my-source.kamelet.yaml";
            } else if (selected[0].contains("sink")) {
                defaultName = "my-sink.kamelet.yaml";
            } else {
                defaultName = "my-action.kamelet.yaml";
            }
        } else if (selected[0].contains("pipe")) {
            defaultName = "my-pipe.yaml";
            pipe = true;
        }

        // Step 3: Prompt for filename
        printer().println();
        printer().printf("Filename [%s]: ", defaultName);
        String filename = scanner.nextLine().trim();
        if (filename.isEmpty()) {
            filename = defaultName;
        }

        this.file = filename;
        overwritePrompt = scanner;
        int code = execute();
        if (code == 0) {
            createWorkingDirectoryIfAbsent();
            printer().println();
            printer().println("Created: " + filename);
            printer().println();
            printer().println("Next steps:");
            printer().println("  Run:           camel run " + filename);
            printer().println("  Run (live):    camel run " + filename + " --dev");
            printer().println("  Documentation: camel doc <component>");
        }
        return code;
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
                List<Resource> resources = new ArrayList<>();
                for (String u : all.toString().split(",")) {
                    Resource resource = resolver.resolve(u);
                    if (!resource.exists()) {
                        throw new ResourceDoesNotExist(resource);
                    }
                    resources.add(resource);
                }
                return writeDownloaded(resources);
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
                List<Resource> resources = new ArrayList<>();
                for (String u : all.toString().split(",")) {
                    Resource resource = resolver.resolve(u);
                    if (!resource.exists()) {
                        throw new ResourceDoesNotExist(resource);
                    }
                    resources.add(resource);
                }
                return writeDownloaded(resources);
            }
        }

        return 0;
    }

}
