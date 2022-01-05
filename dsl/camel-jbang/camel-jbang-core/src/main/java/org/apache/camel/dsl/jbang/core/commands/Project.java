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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import org.apache.camel.dsl.jbang.core.generator.CamelJbangGenerator;
import org.apache.camel.dsl.jbang.core.generator.QuarkusGenerator;
import picocli.CommandLine;

@CommandLine.Command(name = "create-project", description = "Creates Camel-Quarkus project")
public class Project implements Callable<Integer> {

    private static final String PACKAGE_REGEX = "package\\s+([a-zA_Z_][\\.\\w]*);";
    private static final Pattern PACKAGE_PATTERN = Pattern.compile(PACKAGE_REGEX);
    @CommandLine.Parameters(description = "The Camel file(s) to include in the created project", arity = "1")
    private String[] files;
    @CommandLine.Option(names = { "-h", "--help" }, usageHelp = true, description = "Display the help and sub-commands")
    private boolean helpRequested;
    @CommandLine.Option(names = { "-n", "--name" }, description = "The name of the Camel application",
                        defaultValue = "CamelJBang")
    private String name;
    @CommandLine.Option(names = { "-g", "--group-id" }, description = "The group ID of the maven project",
                        defaultValue = "org.apache.camel.jbang")
    private String groupId;
    @CommandLine.Option(names = { "-d", "--directory" }, description = "Directory where the project will be created",
                        defaultValue = ".")
    private String baseDirectory;
    @CommandLine.Option(names = "--quarkus-dependency", description = "Comma separated list of camel-quarkus dependencies",
                        defaultValue = "camel-quarkus-timer,camel-quarkus-log,camel-quarkus-yaml-dsl,camel-quarkus-kamelet,org.apache.camel.kamelets:camel-kamelets-catalog:0.6.0")
    private String quarkusDependencies;
    @CommandLine.Option(names = "--quarkus-bom-version", description = "Override quarkus bom version in pom.xml",
                        defaultValue = "2.6.0.Final")
    private String quarkusBomVersion;

    private CamelJbangGenerator generator;
    private Path resourcesFolder = Paths.get("src", "main", "resources");
    private Path sourcesFolder = Paths.get("src", "main", "java");
    private Path routesFolder = resourcesFolder.resolve("routes");

    @Override
    public Integer call() throws Exception {
        generator = new QuarkusGenerator(Arrays.asList(quarkusDependencies.split(",")), quarkusBomVersion);

        File baseDir = new File(baseDirectory);
        createDirectory(baseDir);

        Path projectPath = Paths.get(baseDirectory, name);
        File projectDir = projectPath.toFile();
        createDirectory(projectDir);

        System.out.println(name + " project will be generated in " + projectDir.getAbsolutePath());

        createProjectStructure(projectPath);

        for (String file : files) {
            if (!file.endsWith(".java")) {
                Files.copy(Paths.get(file), projectPath.resolve(routesFolder).resolve(Paths.get(file).getFileName()));
            } else {
                String packageName = Files.lines(Paths.get(file))
                        .map(fileContent -> getPackage(fileContent))
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse("");
                try {
                    Path packageDirectory
                            = projectPath.resolve(sourcesFolder).resolve(packageName.replace(".", File.separator));
                    if (!packageDirectory.toFile().exists()) {
                        createDirectory(packageDirectory.toFile());
                    }

                    Files.copy(Paths.get(file),
                            packageDirectory.resolve(Paths.get(file).getFileName()));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        return 0;
    }

    private void createProjectStructure(Path projectPath) throws IOException {
        Path javaSourcesPath = projectPath.resolve(sourcesFolder);
        createDirectory(javaSourcesPath.toFile());
        Path resourcesPath = projectPath.resolve(resourcesFolder);
        createDirectory(resourcesPath.toFile());
        Path routes = projectPath.resolve(routesFolder);
        createDirectory(routes.toFile());

        // Create application.properties
        Files.write(projectPath.resolve(generator.getPropertyFileLocation()),
                generator.getPropertyFileContent(name).getBytes());

        // Create pom.xml
        Map root = new HashMap<String, Object>();
        root.put("name", name);
        root.put("groupId", groupId);
        root.put("pomProperties", generator.getPomProperties());
        root.put("pomDependencies", generator.getPomDependencies());

        Configuration cfg = new Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
        cfg.setClassForTemplateLoading(this.getClass(), "/generator");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.IGNORE_HANDLER);

        Template template = cfg.getTemplate(generator.getTemplate());

        try (Writer fileWriter = new FileWriter(projectPath.resolve("pom.xml").toFile())) {
            template.process(root, fileWriter);
        } catch (TemplateException e) {
            throw new RuntimeException(e);
        }
    }

    private String getPackage(String fileContent) {
        Matcher matcher = PACKAGE_PATTERN.matcher(fileContent);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    private void createDirectory(File directory) {
        if (!directory.exists()) {
            directory.mkdirs();
        }
    }
}
