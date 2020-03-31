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
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.camel.tooling.model.BaseModel;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.DataFormatModel;
import org.apache.camel.tooling.model.EipModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.model.LanguageModel;
import org.apache.camel.tooling.model.OtherModel;
import org.apache.camel.tooling.util.FileUtil;
import org.apache.camel.tooling.util.PackageHelper;
import org.apache.camel.tooling.util.Strings;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

import static org.apache.camel.tooling.util.PackageHelper.loadText;

/**
 * Prepares the camel catalog to include component, data format, and eip
 * descriptors, and generates a report.
 */
@Mojo(name = "prepare-catalog", threadSafe = true)
public class PrepareCatalogMojo extends AbstractMojo {

    private static final String[] EXCLUDE_DOC_FILES = {"camel-core-xml", "camel-http-common", "camel-http-base", "camel-jetty-common", "camel-debezium-common"};

    private static final Pattern LABEL_PATTERN = Pattern.compile("\\\"label\\\":\\s\\\"([\\w,]+)\\\"");

    private static final int UNUSED_LABELS_WARN = 15;

    /**
     * The maven project.
     */
    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;

    /**
     * Whether to validate if the components, data formats, and languages are
     * properly documented and have all the needed details.
     */
    @Parameter(defaultValue = "true")
    protected Boolean validate;

    /**
     * The output directory for components catalog
     */
    @Parameter(defaultValue = "${project.build.directory}/classes/org/apache/camel/catalog/components")
    protected File componentsOutDir;

    /**
     * The output directory for dataformats catalog
     */
    @Parameter(defaultValue = "${project.build.directory}/classes/org/apache/camel/catalog/dataformats")
    protected File dataFormatsOutDir;

    /**
     * The output directory for languages catalog
     */
    @Parameter(defaultValue = "${project.build.directory}/classes/org/apache/camel/catalog/languages")
    protected File languagesOutDir;

    /**
     * The output directory for others catalog
     */
    @Parameter(defaultValue = "${project.build.directory}/classes/org/apache/camel/catalog/others")
    protected File othersOutDir;

    /**
     * The output directory for documents catalog
     */
    @Parameter(defaultValue = "${project.build.directory}/classes/org/apache/camel/catalog/docs")
    protected File documentsOutDir;

    /**
     * The output directory for models catalog
     */
    @Parameter(defaultValue = "${project.build.directory}/classes/org/apache/camel/catalog/models")
    protected File modelsOutDir;

    /**
     * The output directory for archetypes catalog
     */
    @Parameter(defaultValue = "${project.build.directory}/classes/org/apache/camel/catalog/archetypes")
    protected File archetypesOutDir;

    /**
     * The output directory for XML schemas catalog
     */
    @Parameter(defaultValue = "${project.build.directory}/classes/org/apache/camel/catalog/schemas")
    protected File schemasOutDir;

    /**
     * The output directory for main
     */
    @Parameter(defaultValue = "${project.build.directory}/classes/org/apache/camel/catalog/main")
    protected File mainOutDir;

    /**
     * The components directory where all the Apache Camel components are
     */
    @Parameter(defaultValue = "${project.build.directory}/../../../components")
    protected File componentsDir;

    /**
     * The camel-core directory
     */
    @Parameter(defaultValue = "${project.build.directory}/../../../core/camel-core-engine")
    protected File coreDir;

    /**
     * The camel-base directory
     */
    @Parameter(defaultValue = "${project.build.directory}/../../../core/camel-base")
    protected File baseDir;

    /**
     * The camel-core-languages directory
     */
    @Parameter(defaultValue = "${project.build.directory}/../../../core/camel-core-languages")
    protected File languagesDir;

    /**
     * The camel-xml-jaxp directory
     */
    @Parameter(defaultValue = "${project.build.directory}/../../../core/camel-xml-jaxp")
    protected File jaxpDir;

    /**
     * The directory where the camel-spring XML models are
     */
    @Parameter(defaultValue = "${project.build.directory}/../../../components/camel-spring")
    protected File springDir;

    /**
     * The archetypes directory where all the Apache Camel Maven archetypes are
     */
    @Parameter(defaultValue = "${project.build.directory}/../../../archetypes")
    protected File archetypesDir;

    /**
     * The directory where the camel-spring XML schema are
     */
    @Parameter(defaultValue = "${project.build.directory}/../../../components/camel-spring/target/schema")
    protected File springSchemaDir;

    /**
     * The directory where the camel-blueprint XML schema are
     */
    @Parameter(defaultValue = "${project.build.directory}/../../../components/camel-blueprint/target/schema")
    protected File blueprintSchemaDir;

    /**
     * The directory where the camel-main metadata are
     */
    @Parameter(defaultValue = "${project.build.directory}/../../../core/camel-main/target/classes/META-INF")
    protected File mainDir;

    /**
     * Maven ProjectHelper.
     */
    @Component
    protected MavenProjectHelper projectHelper;

    private Collection<Path> allJsonFiles;
    private Collection<Path> allPropertiesFiles;
    private Map<Path, BaseModel<?>> allModels;

    /**
     * Execute goal.
     *
     * @throws org.apache.maven.plugin.MojoExecutionException execution of the
     *             main class or one of the threads it generated failed.
     * @throws org.apache.maven.plugin.MojoFailureException something bad
     *             happened...
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            allJsonFiles = new TreeSet<>();
            allPropertiesFiles = new TreeSet<>();

            Stream.concat(list(componentsDir.toPath()), Stream.of(coreDir.toPath(), baseDir.toPath(), languagesDir.toPath(), jaxpDir.toPath(), springDir.toPath()))
                    .filter(dir -> !"target".equals(dir.getFileName().toString())).map(this::getComponentPath).filter(dir -> Files.isDirectory(dir.resolve("src")))
                    .map(p -> p.resolve("target/classes")).flatMap(PackageHelper::walk).forEach(p -> {
                        String f = p.getFileName().toString();
                        if (f.endsWith(PackageHelper.JSON_SUFIX)) {
                            allJsonFiles.add(p);
                        } else if (f.equals("component.properties") || f.equals("dataformat.properties") || f.equals("language.properties") || f.equals("other.properties")) {
                            allPropertiesFiles.add(p);
                        }
                    });
            allModels = allJsonFiles.stream().collect(Collectors.toMap(p -> p, JsonMapper::generateModel));

            executeModel();
            Set<String> components = executeComponents();
            Set<String> dataformats = executeDataFormats();
            Set<String> languages = executeLanguages();
            Set<String> others = executeOthers();
            executeDocuments(components, dataformats, languages, others);
            executeArchetypes();
            executeXmlSchemas();
            executeMain();
        } catch (Exception e) {
            throw new MojoFailureException("Error preparing catalog", e);
        }
    }

    protected void executeModel() throws Exception {
        Path coreDir = this.coreDir.toPath();
        Path springDir = this.springDir.toPath();
        Path modelsOutDir = this.modelsOutDir.toPath();

        getLog().info("================================================================================");
        getLog().info("Copying all Camel model json descriptors");

        // lets use sorted set/maps
        Set<Path> jsonFiles;
        Set<Path> duplicateJsonFiles;
        Set<Path> missingLabels = new TreeSet<>();
        Set<Path> missingJavaDoc = new TreeSet<>();
        Map<String, Set<String>> usedLabels = new TreeMap<>();

        // find all json files in camel-core and camel-spring
        Path coreDirTarget = coreDir.resolve("target/classes/org/apache/camel/model");
        Path springTarget1 = springDir.resolve("target/classes/org/apache/camel/spring");
        Path springTarget2 = springDir.resolve("target/classes/org/apache/camel/core/xml");
        jsonFiles = allJsonFiles.stream().filter(p -> p.startsWith(coreDirTarget) || p.startsWith(springTarget1) || p.startsWith(springTarget2))
                .collect(Collectors.toCollection(TreeSet::new));
        getLog().info("Found " + jsonFiles.size() + " model json files");

        // make sure to create out dir
        Files.createDirectories(modelsOutDir);

        duplicateJsonFiles = getDuplicates(jsonFiles);

        // Copy all descriptors
        Map<Path, Path> newJsons = map(jsonFiles, p -> p, p -> modelsOutDir.resolve(p.getFileName()));
        list(modelsOutDir).filter(p -> !newJsons.containsValue(p)).forEach(this::delete);
        newJsons.forEach(this::copy);

        for (Path file : jsonFiles) {
            // check if we have a label as we want the eip to include labels
            EipModel model = (EipModel)allModels.get(file);

            String name = asComponentName(file);

            // grab the label, and remember it in the used labels
            String label = model.getLabel();
            if (Strings.isNullOrEmpty(label)) {
                missingLabels.add(file);
            } else {
                String[] labels = label.split(",");
                for (String s : labels) {
                    usedLabels.computeIfAbsent(s, k -> new TreeSet<>()).add(name);
                }
            }

            // check all the properties if they have description
            if (model.getOptions().stream().filter(option -> !"outputs".equals(option.getName()) && !"transforms".equals(option.getName())).map(BaseOptionModel::getDescription)
                    .anyMatch(Strings::isNullOrEmpty)) {
                missingJavaDoc.add(file);
            }
        }

        Path all = modelsOutDir.resolve("../models.properties");
        Set<String> modelNames = jsonFiles.stream().map(PrepareCatalogMojo::asComponentName).collect(Collectors.toCollection(TreeSet::new));
        FileUtil.updateFile(all, String.join("\n", modelNames) + "\n");

        printModelsReport(jsonFiles, duplicateJsonFiles, missingLabels, usedLabels, missingJavaDoc);
    }

    // CHECKSTYLE:OFF
    protected Set<String> executeComponents() throws Exception {
        Path coreDir = this.coreDir.toPath();
        Path componentsDir = this.componentsDir.toPath();
        Path componentsOutDir = this.componentsOutDir.toPath();

        getLog().info("Copying all Camel component json descriptors");

        // lets use sorted set/maps
        Set<Path> jsonFiles;
        Set<Path> duplicateJsonFiles;
        Set<Path> componentFiles;
        Set<Path> missingComponents = new TreeSet<>();
        Map<String, Set<String>> usedComponentLabels = new TreeMap<>();
        Set<String> usedOptionLabels = new TreeSet<>();
        Set<String> unlabeledOptions = new TreeSet<>();
        Set<Path> missingFirstVersions = new TreeSet<>();

        // find all json files in components and camel-core
        componentFiles = allPropertiesFiles.stream().filter(p -> p.endsWith("component.properties")).collect(Collectors.toCollection(TreeSet::new));
        jsonFiles = allJsonFiles.stream().filter(p -> allModels.get(p) instanceof ComponentModel).collect(Collectors.toCollection(TreeSet::new));
        componentFiles.stream().filter(p -> p.endsWith("component.properties")).forEach(p -> {
            Path parent = getModule(p);
            List<Path> jsons = jsonFiles.stream().filter(f -> f.startsWith(parent)).collect(Collectors.toList());
            if (jsons.isEmpty()) {
                missingComponents.add(parent);
            }
        });

        getLog().info("Found " + componentFiles.size() + " component.properties files");
        getLog().info("Found " + jsonFiles.size() + " component json files");

        // make sure to create out dir
        Files.createDirectories(componentsOutDir);

        // Check duplicates
        duplicateJsonFiles = getDuplicates(jsonFiles);

        // Copy all descriptors
        Map<Path, Path> newJsons = map(jsonFiles, p -> p, p -> componentsOutDir.resolve(p.getFileName()));
        list(componentsOutDir).filter(p -> !newJsons.containsValue(p)).forEach(this::delete);
        newJsons.forEach(this::copy);

        Set<String> alternativeSchemes = new HashSet<>();

        for (Path file : jsonFiles) {
            // check if we have a component label as we want the components to
            // include labels
            try {
                String text = loadText(file);
                ComponentModel model = JsonMapper.generateComponentModel(text);

                String name = asComponentName(file);

                // grab the label, and remember it in the used labels
                String label = model.getLabel();
                String[] labels = label.split(",");
                for (String s : labels) {
                    Set<String> components = usedComponentLabels.computeIfAbsent(s, k -> new TreeSet<>());
                    components.add(name);
                }

                // check all the component options and grab the label(s) they
                // use
                model.getComponentOptions().stream().map(BaseOptionModel::getLabel).filter(l -> !Strings.isNullOrEmpty(l)).flatMap(l -> Stream.of(label.split(",")))
                        .forEach(usedOptionLabels::add);

                // check all the endpoint options and grab the label(s) they use
                model.getEndpointOptions().stream().map(BaseOptionModel::getLabel).filter(l -> !Strings.isNullOrEmpty(l)).flatMap(l -> Stream.of(label.split(",")))
                        .forEach(usedOptionLabels::add);

                long unused = model.getEndpointOptions().stream().map(BaseOptionModel::getLabel).filter(Strings::isNullOrEmpty).count();
                if (unused >= UNUSED_LABELS_WARN) {
                    unlabeledOptions.add(name);
                }

                // remember alternative schemes
                String alternativeScheme = model.getAlternativeSchemes();
                if (!Strings.isNullOrEmpty(alternativeScheme)) {
                    String[] parts = alternativeScheme.split(",");
                    // skip first as that is the regular scheme
                    alternativeSchemes.addAll(Arrays.asList(parts).subList(1, parts.length));
                }

                // detect missing first version
                String firstVersion = model.getFirstVersion();
                if (Strings.isNullOrEmpty(firstVersion)) {
                    missingFirstVersions.add(file);
                }

            } catch (IOException e) {
                // ignore
            }
        }

        Path all = componentsOutDir.resolve("../components.properties");
        Set<String> componentNames = jsonFiles.stream().map(PrepareCatalogMojo::asComponentName).collect(Collectors.toCollection(TreeSet::new));
        FileUtil.updateFile(all, String.join("\n", componentNames) + "\n");

        printComponentsReport(jsonFiles, duplicateJsonFiles, missingComponents, usedComponentLabels, usedOptionLabels, unlabeledOptions, missingFirstVersions);

        // filter out duplicate component names that are alternative scheme
        // names
        componentNames.removeAll(alternativeSchemes);
        return componentNames;
    }

    protected Set<String> executeDataFormats() throws Exception {
        Path dataFormatsOutDir = this.dataFormatsOutDir.toPath();

        getLog().info("Copying all Camel dataformat json descriptors");

        // lets use sorted set/maps
        Set<Path> jsonFiles;
        Set<Path> duplicateJsonFiles;
        Set<Path> dataFormatFiles;
        Map<String, Set<String>> usedLabels = new TreeMap<>();
        Set<Path> missingFirstVersions = new TreeSet<>();

        // find all data formats from the components directory
        dataFormatFiles = allPropertiesFiles.stream().filter(p -> p.endsWith("dataformat.properties")).collect(Collectors.toCollection(TreeSet::new));
        jsonFiles = allJsonFiles.stream().filter(p -> allModels.get(p) instanceof DataFormatModel).collect(Collectors.toCollection(TreeSet::new));

        getLog().info("Found " + dataFormatFiles.size() + " dataformat.properties files");
        getLog().info("Found " + jsonFiles.size() + " dataformat json files");

        // make sure to create out dir
        Files.createDirectories(dataFormatsOutDir);

        // Check duplicates
        duplicateJsonFiles = getDuplicates(jsonFiles);

        // Copy all descriptors
        Map<Path, Path> newJsons = map(jsonFiles, p -> p, p -> dataFormatsOutDir.resolve(p.getFileName()));
        list(dataFormatsOutDir).filter(p -> !newJsons.containsValue(p)).forEach(this::delete);
        newJsons.forEach(this::copy);

        for (Path file : jsonFiles) {

            DataFormatModel model = (DataFormatModel)allModels.get(file);

            // Check labels
            String name = asComponentName(file);
            for (String s : model.getLabel().split(",")) {
                usedLabels.computeIfAbsent(s, k -> new TreeSet<>()).add(name);
            }

            // detect missing first version
            String firstVersion = model.getFirstVersion();
            if (Strings.isNullOrEmpty(firstVersion)) {
                missingFirstVersions.add(file);
            }
        }

        Path all = dataFormatsOutDir.resolve("../dataformats.properties");
        Set<String> dataFormatNames = jsonFiles.stream().map(PrepareCatalogMojo::asComponentName).collect(Collectors.toCollection(TreeSet::new));
        FileUtil.updateFile(all, String.join("\n", dataFormatNames) + "\n");

        printDataFormatsReport(jsonFiles, duplicateJsonFiles, usedLabels, missingFirstVersions);

        return dataFormatNames;
    }

    protected Set<String> executeLanguages() throws Exception {
        Path languagesOutDir = this.languagesOutDir.toPath();

        getLog().info("Copying all Camel language json descriptors");

        // lets use sorted set/maps
        Set<Path> jsonFiles;
        Set<Path> duplicateJsonFiles;
        Set<Path> languageFiles;
        Map<String, Set<String>> usedLabels = new TreeMap<>();
        Set<Path> missingFirstVersions = new TreeSet<>();

        // find all languages from the components directory
        languageFiles = allPropertiesFiles.stream().filter(p -> p.endsWith("language.properties")).collect(Collectors.toCollection(TreeSet::new));
        jsonFiles = allJsonFiles.stream().filter(p -> allModels.get(p) instanceof LanguageModel).collect(Collectors.toCollection(TreeSet::new));

        getLog().info("Found " + languageFiles.size() + " language.properties files");
        getLog().info("Found " + jsonFiles.size() + " language json files");

        // make sure to create out dir
        Files.createDirectories(languagesOutDir);

        // Check duplicates
        duplicateJsonFiles = getDuplicates(jsonFiles);

        // Copy all descriptors
        Map<Path, Path> newJsons = map(jsonFiles, p -> p, p -> languagesOutDir.resolve(p.getFileName()));
        list(languagesOutDir).filter(p -> !newJsons.containsValue(p)).forEach(this::delete);
        newJsons.forEach(this::copy);

        for (Path file : jsonFiles) {

            LanguageModel model = (LanguageModel)allModels.get(file);

            // Check labels
            String name = asComponentName(file);
            for (String s : model.getLabel().split(",")) {
                usedLabels.computeIfAbsent(s, k -> new TreeSet<>()).add(name);
            }

            // detect missing first version
            String firstVersion = model.getFirstVersion();
            if (Strings.isNullOrEmpty(firstVersion)) {
                missingFirstVersions.add(file);
            }
        }

        Path all = languagesOutDir.resolve("../languages.properties");
        Set<String> languagesNames = jsonFiles.stream().map(PrepareCatalogMojo::asComponentName).collect(Collectors.toCollection(TreeSet::new));
        FileUtil.updateFile(all, String.join("\n", languagesNames) + "\n");

        printLanguagesReport(jsonFiles, duplicateJsonFiles, usedLabels, missingFirstVersions);

        return languagesNames;
    }

    private Set<String> executeOthers() throws Exception {
        Path othersOutDir = this.othersOutDir.toPath();

        getLog().info("Copying all Camel other json descriptors");

        // lets use sorted set/maps
        Set<Path> jsonFiles;
        Set<Path> duplicateJsonFiles;
        Set<Path> otherFiles;
        Map<String, Set<String>> usedLabels = new TreeMap<>();
        Set<Path> missingFirstVersions = new TreeSet<>();

        otherFiles = allPropertiesFiles.stream().filter(p -> p.endsWith("other.properties")).collect(Collectors.toCollection(TreeSet::new));
        jsonFiles = allJsonFiles.stream().filter(p -> {
            Path m = getModule(p);
            switch (m.getFileName().toString()) {
                case "camel-core-xml":
                case "camel-box":
                case "camel-http-base":
                case "camel-http-common":
                case "camel-jetty-common":
                case "camel-as2":
                case "camel-olingo2":
                case "camel-olingo4":
                case "camel-servicenow":
                case "camel-salesforce":
                case "camel-fhir":
                case "camel-debezium-common":
                    return false;
                default:
                    return true;
            }
        }).filter(p -> allModels.get(p) instanceof OtherModel).collect(Collectors.toCollection(TreeSet::new));

        getLog().info("Found " + otherFiles.size() + " other.properties files");
        getLog().info("Found " + jsonFiles.size() + " other json files");

        // make sure to create out dir
        Files.createDirectories(othersOutDir);

        // Check duplicates
        duplicateJsonFiles = getDuplicates(jsonFiles);

        // Copy all descriptors
        Map<Path, Path> newJsons = map(jsonFiles, p -> p, p -> othersOutDir.resolve(p.getFileName()));
        list(othersOutDir).filter(p -> !newJsons.containsValue(p)).forEach(this::delete);
        newJsons.forEach(this::copy);

        for (Path file : jsonFiles) {

            OtherModel model = (OtherModel)allModels.get(file);

            String name = asComponentName(file);

            // grab the label, and remember it in the used labels
            String label = model.getLabel();
            if (!Strings.isNullOrEmpty(label)) {
                String[] labels = label.split(",");
                for (String s : labels) {
                    usedLabels.computeIfAbsent(s, k -> new TreeSet<>()).add(name);
                }
            }

            // detect missing first version
            String firstVersion = model.getFirstVersion();
            if (Strings.isNullOrEmpty(firstVersion)) {
                missingFirstVersions.add(file);
            }
        }

        Path all = othersOutDir.resolve("../others.properties");
        Set<String> otherNames = jsonFiles.stream().map(PrepareCatalogMojo::asComponentName).collect(Collectors.toCollection(TreeSet::new));
        FileUtil.updateFile(all, String.join("\n", otherNames) + "\n");

        printOthersReport(jsonFiles, duplicateJsonFiles, usedLabels, missingFirstVersions);

        return otherNames;
    }

    protected void executeArchetypes() throws Exception {
        Path archetypesDir = this.archetypesDir.toPath();
        Path archetypesOutDir = this.archetypesOutDir.toPath();

        getLog().info("Copying Archetype Catalog");

        // find the generate catalog
        copyFile(archetypesDir.resolve("target/classes/archetype-catalog.xml"), archetypesOutDir);
    }

    protected void executeXmlSchemas() throws Exception {
        Path schemasOutDir = this.schemasOutDir.toPath();
        Path springSchemaDir = this.springSchemaDir.toPath();
        Path blueprintSchemaDir = this.blueprintSchemaDir.toPath();

        getLog().info("Copying Spring/Blueprint XML schemas");

        copyFile(springSchemaDir.resolve("camel-spring.xsd"), schemasOutDir);
        copyFile(blueprintSchemaDir.resolve("camel-blueprint.xsd"), schemasOutDir);
    }

    protected void executeMain() throws Exception {
        getLog().info("Copying camel-main metadata");

        copyFile(mainDir.toPath().resolve("camel-main-configuration-metadata.json"), mainOutDir.toPath());
    }

    protected void executeDocuments(Set<String> components, Set<String> dataformats, Set<String> languages, Set<String> others) throws Exception {
        Path documentsOutDir = this.documentsOutDir.toPath();

        getLog().info("Copying all Camel documents (ascii docs)");

        // lets use sorted set/maps
        Set<Path> adocFiles = new TreeSet<>();
        Set<Path> missingAdocFiles = new TreeSet<>();
        Set<Path> duplicateAdocFiles = new TreeSet<>();

        // find all camel maven modules
        Stream.concat(list(componentsDir.toPath()).filter(dir -> !"target".equals(dir.getFileName().toString())).map(this::getComponentPath),
                Stream.of(coreDir.toPath(), baseDir.toPath(), languagesDir.toPath(), jaxpDir.toPath()))
                .forEach(dir -> {
                    List<Path> l = PackageHelper.walk(dir.resolve("src/main/docs")).filter(f -> f.getFileName().toString().endsWith(".adoc")).collect(Collectors.toList());
                    if (l.isEmpty()) {
                        missingAdocFiles.add(dir);
                    }
                    adocFiles.addAll(l);
                });

        getLog().info("Found " + adocFiles.size() + " ascii document files");

        // make sure to create out dir
        Files.createDirectories(documentsOutDir);

        // Check duplicates
        duplicateAdocFiles = getDuplicates(adocFiles);

        // Copy all descriptors
        Map<Path, Path> newJsons = map(adocFiles, p -> p, p -> documentsOutDir.resolve(p.getFileName()));
        list(documentsOutDir).filter(p -> !newJsons.containsValue(p) && !newJsons.containsValue(p.resolveSibling(p.getFileName().toString().replace(".html", ".adoc"))))
                .forEach(this::delete);
        newJsons.forEach(this::copy);

        Path all = documentsOutDir.resolve("../docs.properties");
        Set<String> docNames = adocFiles.stream().map(PrepareCatalogMojo::asComponentName).collect(Collectors.toCollection(TreeSet::new));
        FileUtil.updateFile(all, String.join("\n", docNames) + "\n");

        printDocumentsReport(adocFiles, duplicateAdocFiles, missingAdocFiles);

        // find out if we have documents for each component / dataformat /
        // languages / others
        printMissingDocumentsReport(docNames, components, dataformats, languages, others);
    }

    private void printMissingDocumentsReport(Set<String> docs, Set<String> components, Set<String> dataformats, Set<String> languages, Set<String> others) {
        getLog().info("");
        getLog().info("Camel missing documents report");
        getLog().info("");

        List<String> missing = new ArrayList<>();
        for (String component : components) {
            // special for mail
            switch (component) {
                case "imap":
                case "imaps":
                case "pop3":
                case "pop3s":
                case "smtp":
                case "smtps":
                    component = "mail";
                    break;
                case "ftp":
                case "sftp":
                case "ftps":
                    component = "ftp";
                    break;
            }
            String name = component + "-component";
            if (!docs.contains(name) && (!component.equalsIgnoreCase("salesforce") && !component.equalsIgnoreCase("servicenow"))) {
                missing.add(name);
            }
        }
        if (!missing.isEmpty()) {
            getLog().info("");
            getLog().warn("\tMissing .adoc component documentation: " + missing.size());
            for (String name : missing) {
                getLog().warn("\t\t" + name);
            }
        }
        missing.clear();

        for (String dataformat : dataformats) {
            // special for bindy
            if (dataformat.startsWith("bindy")) {
                dataformat = "bindy";
            }
            String name = dataformat + "-dataformat";
            if (!docs.contains(name)) {
                missing.add(name);
            }
        }
        if (!missing.isEmpty()) {
            getLog().info("");
            getLog().warn("\tMissing .adoc dataformat documentation: " + missing.size());
            for (String name : missing) {
                getLog().warn("\t\t" + name);
            }
        }
        missing.clear();

        for (String language : languages) {
            String name = language + "-language";
            if (!docs.contains(name)) {
                missing.add(name);
            }
        }
        if (!missing.isEmpty()) {
            getLog().info("");
            getLog().warn("\tMissing .adoc language documentation: " + missing.size());
            for (String name : missing) {
                getLog().warn("\t\t" + name);
            }
        }
        missing.clear();

        for (String other : others) {
            String name = other;
            if (!docs.contains(name)) {
                missing.add(name);
            }
        }
        if (!missing.isEmpty()) {
            getLog().info("");
            getLog().warn("\tMissing .adoc other documentation: " + missing.size());
            for (String name : missing) {
                getLog().warn("\t\t" + name);
            }
        }
        missing.clear();

        getLog().info("");
        getLog().info("================================================================================");
    }

    private void printModelsReport(Set<Path> json, Set<Path> duplicate, Set<Path> missingLabels, Map<String, Set<String>> usedLabels, Set<Path> missingJavaDoc) {
        getLog().info("================================================================================");

        getLog().info("");
        getLog().info("Camel model catalog report");
        getLog().info("");
        getLog().info("\tModels found: " + json.size());
        for (Path file : json) {
            getLog().info("\t\t" + asComponentName(file));
        }
        if (!duplicate.isEmpty()) {
            getLog().info("");
            getLog().warn("\tDuplicate models detected: " + duplicate.size());
            for (Path file : duplicate) {
                getLog().warn("\t\t" + asComponentName(file));
            }
        }
        if (!missingLabels.isEmpty()) {
            getLog().info("");
            getLog().warn("\tMissing labels detected: " + missingLabels.size());
            for (Path file : missingLabels) {
                getLog().warn("\t\t" + asComponentName(file));
            }
        }
        if (!usedLabels.isEmpty()) {
            getLog().info("");
            getLog().info("\tUsed labels: " + usedLabels.size());
            for (Map.Entry<String, Set<String>> entry : usedLabels.entrySet()) {
                getLog().info("\t\t" + entry.getKey() + ":");
                for (String name : entry.getValue()) {
                    getLog().info("\t\t\t" + name);
                }
            }
        }
        if (!missingJavaDoc.isEmpty()) {
            getLog().info("");
            getLog().warn("\tMissing javadoc on models: " + missingJavaDoc.size());
            for (Path file : missingJavaDoc) {
                getLog().warn("\t\t" + asComponentName(file));
            }
        }
        getLog().info("");
        getLog().info("================================================================================");
    }

    private void printComponentsReport(Set<Path> json, Set<Path> duplicate, Set<Path> missing, Map<String, Set<String>> usedComponentLabels, Set<String> usedOptionsLabels,
                                       Set<String> unusedLabels, Set<Path> missingFirstVersions) {
        getLog().info("================================================================================");
        getLog().info("");
        getLog().info("Camel component catalog report");
        getLog().info("");
        getLog().info("\tComponents found: " + json.size());
        for (Path file : json) {
            getLog().info("\t\t" + asComponentName(file));
        }
        if (!duplicate.isEmpty()) {
            getLog().info("");
            getLog().warn("\tDuplicate components detected: " + duplicate.size());
            for (Path file : duplicate) {
                getLog().warn("\t\t" + asComponentName(file));
            }
        }
        if (!usedComponentLabels.isEmpty()) {
            getLog().info("");
            getLog().info("\tUsed component labels: " + usedComponentLabels.size());
            for (Map.Entry<String, Set<String>> entry : usedComponentLabels.entrySet()) {
                getLog().info("\t\t" + entry.getKey() + ":");
                for (String name : entry.getValue()) {
                    getLog().info("\t\t\t" + name);
                }
            }
        }
        if (!usedOptionsLabels.isEmpty()) {
            getLog().info("");
            getLog().info("\tUsed component/endpoint options labels: " + usedOptionsLabels.size());
            for (String name : usedOptionsLabels) {
                getLog().info("\t\t\t" + name);
            }
        }
        if (!unusedLabels.isEmpty()) {
            getLog().info("");
            getLog().info("\tComponent with more than " + UNUSED_LABELS_WARN + " unlabelled options: " + unusedLabels.size());
            for (String name : unusedLabels) {
                getLog().info("\t\t\t" + name);
            }
        }
        if (!missing.isEmpty()) {
            getLog().info("");
            getLog().warn("\tMissing components detected: " + missing.size());
            for (Path name : missing) {
                getLog().warn("\t\t" + name.getFileName().toString());
            }
        }
        if (!missingFirstVersions.isEmpty()) {
            getLog().info("");
            getLog().warn("\tComponents without firstVersion defined: " + missingFirstVersions.size());
            for (Path name : missingFirstVersions) {
                getLog().warn("\t\t" + name.getFileName().toString());
            }
        }
        getLog().info("");
        getLog().info("================================================================================");
    }

    private void printDataFormatsReport(Set<Path> json, Set<Path> duplicate, Map<String, Set<String>> usedLabels, Set<Path> missingFirstVersions) {
        getLog().info("================================================================================");
        getLog().info("");
        getLog().info("Camel data format catalog report");
        getLog().info("");
        getLog().info("\tDataFormats found: " + json.size());
        for (Path file : json) {
            getLog().info("\t\t" + asComponentName(file));
        }
        if (!duplicate.isEmpty()) {
            getLog().info("");
            getLog().warn("\tDuplicate dataformat detected: " + duplicate.size());
            for (Path file : duplicate) {
                getLog().warn("\t\t" + asComponentName(file));
            }
        }
        if (!usedLabels.isEmpty()) {
            getLog().info("");
            getLog().info("\tUsed labels: " + usedLabels.size());
            for (Map.Entry<String, Set<String>> entry : usedLabels.entrySet()) {
                getLog().info("\t\t" + entry.getKey() + ":");
                for (String name : entry.getValue()) {
                    getLog().info("\t\t\t" + name);
                }
            }
        }
        if (!missingFirstVersions.isEmpty()) {
            getLog().info("");
            getLog().warn("\tDataFormats without firstVersion defined: " + missingFirstVersions.size());
            for (Path name : missingFirstVersions) {
                getLog().warn("\t\t" + name.getFileName().toString());
            }
        }
        getLog().info("");
        getLog().info("================================================================================");
    }

    private void printLanguagesReport(Set<Path> json, Set<Path> duplicate, Map<String, Set<String>> usedLabels, Set<Path> missingFirstVersions) {
        getLog().info("================================================================================");
        getLog().info("");
        getLog().info("Camel language catalog report");
        getLog().info("");
        getLog().info("\tLanguages found: " + json.size());
        for (Path file : json) {
            getLog().info("\t\t" + asComponentName(file));
        }
        if (!duplicate.isEmpty()) {
            getLog().info("");
            getLog().warn("\tDuplicate language detected: " + duplicate.size());
            for (Path file : duplicate) {
                getLog().warn("\t\t" + asComponentName(file));
            }
        }
        if (!usedLabels.isEmpty()) {
            getLog().info("");
            getLog().info("\tUsed labels: " + usedLabels.size());
            for (Map.Entry<String, Set<String>> entry : usedLabels.entrySet()) {
                getLog().info("\t\t" + entry.getKey() + ":");
                for (String name : entry.getValue()) {
                    getLog().info("\t\t\t" + name);
                }
            }
        }
        if (!missingFirstVersions.isEmpty()) {
            getLog().info("");
            getLog().warn("\tLanguages without firstVersion defined: " + missingFirstVersions.size());
            for (Path name : missingFirstVersions) {
                getLog().warn("\t\t" + name.getFileName().toString());
            }
        }
        getLog().info("");
        getLog().info("================================================================================");
    }

    private void printOthersReport(Set<Path> json, Set<Path> duplicate, Map<String, Set<String>> usedLabels, Set<Path> missingFirstVersions) {
        getLog().info("================================================================================");
        getLog().info("");
        getLog().info("Camel other catalog report");
        getLog().info("");
        getLog().info("\tOthers found: " + json.size());
        for (Path file : json) {
            getLog().info("\t\t" + asComponentName(file));
        }
        if (!duplicate.isEmpty()) {
            getLog().info("");
            getLog().warn("\tDuplicate other detected: " + duplicate.size());
            for (Path file : duplicate) {
                getLog().warn("\t\t" + asComponentName(file));
            }
        }
        if (!usedLabels.isEmpty()) {
            getLog().info("");
            getLog().info("\tUsed labels: " + usedLabels.size());
            for (Map.Entry<String, Set<String>> entry : usedLabels.entrySet()) {
                getLog().info("\t\t" + entry.getKey() + ":");
                for (String name : entry.getValue()) {
                    getLog().info("\t\t\t" + name);
                }
            }
        }
        if (!missingFirstVersions.isEmpty()) {
            getLog().info("");
            getLog().warn("\tOthers without firstVersion defined: " + missingFirstVersions.size());
            for (Path name : missingFirstVersions) {
                getLog().warn("\t\t" + name.getFileName().toString());
            }
        }
        getLog().info("");
        getLog().info("================================================================================");
    }

    private void printDocumentsReport(Set<Path> docs, Set<Path> duplicate, Set<Path> missing) {
        getLog().info("================================================================================");
        getLog().info("");
        getLog().info("Camel document catalog report");
        getLog().info("");
        getLog().info("\tDocuments found: " + docs.size());
        for (Path file : docs) {
            getLog().info("\t\t" + asComponentName(file));
        }
        if (!duplicate.isEmpty()) {
            getLog().info("");
            getLog().warn("\tDuplicate document detected: " + duplicate.size());
            for (Path file : duplicate) {
                getLog().warn("\t\t" + asComponentName(file));
            }
        }
        getLog().info("");
        if (!missing.isEmpty()) {
            getLog().info("");
            getLog().warn("\tMissing document detected: " + missing.size());
            for (Path name : missing) {
                getLog().warn("\t\t" + name.getFileName().toString());
            }
        }
        getLog().info("");
        getLog().info("================================================================================");
    }

    private static String asComponentName(Path file) {
        String name = file.getFileName().toString();
        if (name.endsWith(PackageHelper.JSON_SUFIX)) {
            return name.substring(0, name.length() - PackageHelper.JSON_SUFIX.length());
        } else if (name.endsWith(".adoc")) {
            return name.substring(0, name.length() - ".adoc".length());
        }
        return name;
    }

    private void copyFile(Path file, Path toDir) throws IOException, MojoFailureException {
        if (Files.isRegularFile(file)) {
            // make sure to create out dir
            Files.createDirectories(toDir);
            // copy the file
            Path to = toDir.resolve(file.getFileName());
            try {
                FileUtil.updateFile(file, to);
            } catch (IOException e) {
                throw new MojoFailureException("Cannot copy file from " + file + " -> " + to, e);
            }
        }
    }

    private static boolean excludeDocumentDir(String name) {
        for (String exclude : EXCLUDE_DOC_FILES) {
            if (exclude.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private List<Path> concat(List<Path> l1, List<Path> l2) {
        return Stream.concat(l1.stream(), l2.stream()).collect(Collectors.toList());
    }

    // CHECKSTYLE:ON

    private Stream<Path> list(Path dir) {
        try {
            if (Files.isDirectory(dir)) {
                return Files.list(dir);
            } else {
                return Stream.empty();
            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to list files in directory: " + dir, e);
        }
    }

    private void delete(Path dir) {
        try {
            Files.delete(dir);
        } catch (IOException e) {
            throw new RuntimeException("Unable to delete file: " + dir, e);
        }
    }

    private void copy(Path file, Path to) {
        try {
            try {
                BasicFileAttributes af = Files.readAttributes(file, BasicFileAttributes.class);
                BasicFileAttributes at = Files.readAttributes(to, BasicFileAttributes.class);
                if (af.isRegularFile() && at.isRegularFile() && af.size() == at.size() && af.lastModifiedTime().compareTo(at.lastAccessTime()) < 0) {
                    // if same size and not modified, assume the same
                    return;
                }
            } catch (IOException e) {
                // ignore and copy
            }
            FileUtil.updateFile(file, to);
        } catch (IOException e) {
            throw new RuntimeException("Cannot copy file from " + file + " -> " + to, e);
        }
    }

    private <U, K, V> Map<K, V> map(Collection<U> col, Function<U, K> key, Function<U, V> value) {
        return col.stream().collect(Collectors.toMap(key, value));
    }

    private <U, K, V> Map<K, V> map(Collection<U> col, Function<U, K> key, Function<U, V> value, BinaryOperator<V> merger) {
        return col.stream().collect(Collectors.toMap(key, value, merger));
    }

    private Path getModule(Path p) {
        Path parent = p;
        while (!parent.endsWith("target")) {
            parent = parent.getParent();
        }
        return parent.getParent();
    }

    private Set<Path> getDuplicates(Set<Path> jsonFiles) {
        Map<String, List<Path>> byName = map(jsonFiles, PrepareCatalogMojo::asComponentName, // key
                // by
                // component
                // name
                Collections::singletonList, // value
                // as a
                // singleton
                // list
                this::concat); // merge lists
        return byName.values().stream().flatMap(l -> l.stream().skip(1)).collect(Collectors.toCollection(TreeSet::new));
    }

    private Path getComponentPath(Path dir) {
        switch (dir.getFileName().toString()) {
            case "camel-as2":
                return dir.resolve("camel-as2-component");
            case "camel-salesforce":
                return dir.resolve("camel-salesforce-component");
            case "camel-olingo2":
                return dir.resolve("camel-olingo2-component");
            case "camel-olingo4":
                return dir.resolve("camel-olingo4-component");
            case "camel-box":
                return dir.resolve("camel-box-component");
            case "camel-servicenow":
                return dir.resolve("camel-servicenow-component");
            case "camel-fhir":
                return dir.resolve("camel-fhir-component");
            default:
                return dir;
        }
    }

}
