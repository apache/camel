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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BinaryOperator;
import java.util.function.Function;
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

import static org.apache.camel.maven.packaging.MojoHelper.getComponentPath;
import static org.apache.camel.tooling.util.PackageHelper.loadText;

/**
 * Prepares the camel catalog to include component, data format, and eip descriptors, and generates a report.
 */
@Mojo(name = "prepare-catalog", threadSafe = true)
public class PrepareCatalogMojo extends AbstractMojo {

    private static final int UNUSED_LABELS_WARN = 15;

    /**
     * The maven project.
     */
    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;

    /**
     * Whether to validate if the components, data formats, and languages are properly documented and have all the
     * needed details.
     */
    @Parameter(defaultValue = "true")
    protected Boolean validate;

    /**
     * The output directory for components catalog
     */
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources/org/apache/camel/catalog/components")
    protected File componentsOutDir;

    /**
     * The output directory for dataformats catalog
     */
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources/org/apache/camel/catalog/dataformats")
    protected File dataFormatsOutDir;

    /**
     * The output directory for languages catalog
     */
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources/org/apache/camel/catalog/languages")
    protected File languagesOutDir;

    /**
     * The output directory for others catalog
     */
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources/org/apache/camel/catalog/others")
    protected File othersOutDir;

    /**
     * The output directory for models catalog
     */
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources/org/apache/camel/catalog/models")
    protected File modelsOutDir;

    /**
     * The output directory for archetypes catalog
     */
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources/org/apache/camel/catalog/archetypes")
    protected File archetypesOutDir;

    /**
     * The output directory for XML schemas catalog
     */
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources/org/apache/camel/catalog/schemas")
    protected File schemasOutDir;

    /**
     * The output directory for main
     */
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources/org/apache/camel/catalog/main")
    protected File mainOutDir;

    /**
     * The components directory where all the Apache Camel components are
     */
    @Parameter(defaultValue = "${project.build.directory}/../../../components")
    protected File componentsDir;

    /**
     * The camel-core directory
     */
    @Parameter(defaultValue = "${project.build.directory}/../../../core/")
    protected File coreDir;

    /**
     * The camel-model directory
     */
    @Parameter(defaultValue = "${project.build.directory}/../../../core/camel-core-model")
    protected File modelDir;

    /**
     * The DSL directory
     */
    @Parameter(defaultValue = "${project.build.directory}/../../../dsl/")
    protected File dslDir;

    /**
     * The camel-core-languages directory
     */
    @Parameter(defaultValue = "${project.build.directory}/../../../core/camel-core-languages")
    protected File languagesDir;

    /**
     * The directory where the camel-spring XML models are
     */
    @Parameter(defaultValue = "${project.build.directory}/../../../components/camel-spring-xml")
    protected File springDir;

    /**
     * The archetypes directory where all the Apache Camel Maven archetypes are
     */
    @Parameter(defaultValue = "${project.build.directory}/../../../archetypes")
    protected File archetypesDir;

    /**
     * The directory where the camel-spring XML schema are
     */
    @Parameter(defaultValue = "${project.build.directory}/../../../components/camel-spring-xml/target/schema")
    protected File springSchemaDir;

    /**
     * The directory where the camel-main metadata are
     */
    @Parameter(defaultValue = "${project.build.directory}/../../../core/camel-main/target/classes/META-INF")
    protected File mainDir;

    /**
     * Skip the execution of this mojo
     */
    @Parameter(defaultValue = "false", property = "camel.prepare-catalog.skip")
    protected boolean skip;

    /**
     * Maven ProjectHelper.
     */
    @Component
    protected MavenProjectHelper projectHelper;

    private Collection<Path> allJsonFiles;
    private Collection<Path> allPropertiesFiles;
    private final Map<Path, BaseModel<?>> allModels = new HashMap<>();

    private static String asComponentName(Path file) {
        String name = file.getFileName().toString();
        if (name.endsWith(PackageHelper.JSON_SUFIX)) {
            return name.substring(0, name.length() - PackageHelper.JSON_SUFIX.length());
        } else if (name.endsWith(".adoc")) {
            return name.substring(0, name.length() - ".adoc".length());
        }
        return name;
    }

    /**
     * Execute goal.
     *
     * @throws org.apache.maven.plugin.MojoExecutionException execution of the main class or one of the threads it
     *                                                        generated failed.
     * @throws org.apache.maven.plugin.MojoFailureException   something bad happened...
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            allJsonFiles = new TreeSet<>();
            allPropertiesFiles = new TreeSet<>();

            try (Stream<Path> paths
                    = Stream.of(list(coreDir.toPath()), list(componentsDir.toPath())).flatMap(s -> s);
                 Stream<Path> stream = Stream.concat(paths,
                         Stream.of(languagesDir.toPath(), springDir.toPath()))
                         .filter(dir -> !"target".equals(dir.getFileName().toString()))
                         .flatMap(p -> getComponentPath(p).stream())
                         .filter(dir -> Files.isDirectory(dir.resolve("src")))
                         .map(p -> p.resolve("target/classes"))
                         .flatMap(PackageHelper::walk)
                         .filter(Files::isRegularFile)) {
                stream
                        .forEach(p -> {
                            String f = p.getFileName().toString();
                            if (f.endsWith(PackageHelper.JSON_SUFIX)) {
                                allJsonFiles.add(p);
                            } else if (f.equals("component.properties") || f.equals("dataformat.properties")
                                    || f.equals("language.properties") || f.equals("other.properties")) {
                                allPropertiesFiles.add(p);
                            }
                        });
            }

            for (Path p : allJsonFiles) {
                var m = JsonMapper.generateModel(p);
                if (m != null) {
                    allModels.put(p, m);
                }
            }

            // special for dsl-dir as its built after camel-catalog, so we can only look inside src/generated
            try (Stream<Path> stream = Stream.of(list(dslDir.toPath())).flatMap(s -> s)
                    .flatMap(p -> getComponentPath(p).stream())
                    .filter(dir -> Files.isDirectory(dir.resolve("src/generated/resources")))
                    .map(p -> p.resolve("src/generated/resources"))
                    .flatMap(PackageHelper::walk)
                    .filter(Files::isRegularFile)) {
                stream
                        .forEach(p -> {
                            String f = p.getFileName().toString();
                            if (f.endsWith(PackageHelper.JSON_SUFIX)) {
                                allJsonFiles.add(p);
                                var m = JsonMapper.generateModel(p);
                                if (m instanceof OtherModel) {
                                    OtherModel om = (OtherModel) m;
                                    if (!project.getVersion().equals(om.getVersion())) {
                                        // update version in model and file because we prepare catalog before we build DSL
                                        // so their previous generated model files may use previous version (eg 3.20.0-SNAPSHOT -> 3.15.0)
                                        try {
                                            String s = Files.readString(p);
                                            s = s.replaceAll(om.getVersion(), project.getVersion());
                                            FileUtil.updateFile(p, s);
                                        } catch (IOException e) {
                                            // ignore
                                        }
                                        om.setVersion(project.getVersion());
                                    }
                                    allModels.put(p, m);
                                }
                            }
                        });
            }

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
        Path modelDir = this.modelDir.toPath();
        this.springDir.toPath();
        Path modelsOutDir = this.modelsOutDir.toPath();

        getLog().info("================================================================================");
        getLog().info("Copying all Camel model json descriptors");

        // lets use sorted set/maps
        Set<Path> jsonFiles;
        Set<Path> duplicateJsonFiles;
        Set<Path> missingLabels = new TreeSet<>();
        Set<Path> missingJavaDoc = new TreeSet<>();
        Map<String, Set<String>> usedLabels = new TreeMap<>();

        // find all json files in camel-core
        Path coreDirTarget = modelDir.resolve("target/classes/org/apache/camel/model");
        jsonFiles = allJsonFiles.stream()
                .filter(p -> p.startsWith(coreDirTarget))
                .collect(Collectors.toCollection(TreeSet::new));
        getLog().info("Found " + jsonFiles.size() + " model json files");

        // make sure to create out dir
        Files.createDirectories(modelsOutDir);

        duplicateJsonFiles = getDuplicates(jsonFiles);

        // Copy all descriptors
        Map<Path, Path> newJsons = map(jsonFiles, p -> p, p -> modelsOutDir.resolve(p.getFileName()));
        try (Stream<Path> stream = list(modelsOutDir).filter(p -> !newJsons.containsValue(p))) {
            stream.forEach(this::delete);
        }
        newJsons.forEach(this::copy);

        for (Path file : jsonFiles) {
            // check if we have a label as we want the eip to include labels
            EipModel model = (EipModel) allModels.get(file);

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
            if (model.getOptions().stream()
                    .filter(option -> !"outputs".equals(option.getName()) && !"transforms".equals(option.getName()))
                    .map(BaseOptionModel::getDescription)
                    .anyMatch(Strings::isNullOrEmpty)) {
                missingJavaDoc.add(file);
            }
        }

        Path all = modelsOutDir.resolve("../models.properties");
        Set<String> modelNames
                = jsonFiles.stream().map(PrepareCatalogMojo::asComponentName).collect(Collectors.toCollection(TreeSet::new));
        FileUtil.updateFile(all, String.join("\n", modelNames) + "\n");

        printModelsReport(jsonFiles, duplicateJsonFiles, missingLabels, usedLabels, missingJavaDoc);
    }

    // CHECKSTYLE:OFF
    protected Set<String> executeComponents() throws Exception {
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
        try (Stream<Path> stream = list(componentsOutDir).filter(p -> !newJsons.containsValue(p))) {
            stream.forEach(this::delete);
        }
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
        try (Stream<Path> stream = list(dataFormatsOutDir).filter(p -> !newJsons.containsValue(p))) {
            stream.forEach(this::delete);
        }
        newJsons.forEach(this::copy);

        for (Path file : jsonFiles) {

            DataFormatModel model = (DataFormatModel) allModels.get(file);

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
        try (Stream<Path> stream = list(languagesOutDir).filter(p -> !newJsons.containsValue(p))) {
            stream.forEach(this::delete);
        }
        newJsons.forEach(this::copy);

        for (Path file : jsonFiles) {

            LanguageModel model = (LanguageModel) allModels.get(file);

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
                // we want to skip some JARs from core
                case "camel-api":
                case "camel-base":
                case "camel-base-engine":
                case "camel-core":
                case "camel-core-catalog":
                case "camel-core-engine":
                case "camel-core-languages":
                case "camel-core-model":
                case "camel-core-processor":
                case "camel-core-reifier":
                case "camel-core-xml":
                case "camel-management-api":
                case "camel-support":
                case "camel-util":
                case "camel-xml-io":
                case "camel-xml-io-util":
                case "camel-xml-jaxb":
                case "camel-xml-jaxp":
                    // and some from dsl
                case "dsl-support":
                case "camel-dsl-support":
                case "camel-endpointdsl-support":
                    // and components with middle folders
                case "camel-as2":
                case "camel-avro-rpc":
                case "camel-aws":
                case "camel-azure":
                case "camel-box":
                case "camel-cxf":
                case "camel-debezium":
                case "camel-debezium-common":
                case "camel-fhir":
                case "camel-google":
                case "camel-http-base":
                case "camel-http-common":
                case "camel-huawei":
                case "camel-infinispan":
                case "camel-jetty-common":
                case "camel-kantive":
                case "camel-microprofile":
                case "camel-olingo2":
                case "camel-olingo4":
                case "camel-salesforce":
                case "camel-servicenow":
                case "camel-test":
                case "camel-vertx":
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
        try (Stream<Path> stream = list(othersOutDir).filter(p -> !newJsons.containsValue(p))) {
            stream.forEach(this::delete);
        }
        newJsons.forEach(this::copy);

        for (Path file : jsonFiles) {

            OtherModel model = (OtherModel) allModels.get(file);

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

        getLog().info("Copying Spring XML schema");

        copyFile(springSchemaDir.resolve("camel-spring.xsd"), schemasOutDir);
    }

    protected void executeMain() throws Exception {
        getLog().info("Copying camel-main metadata");

        copyFile(mainDir.toPath().resolve("camel-main-configuration-metadata.json"), mainOutDir.toPath());
    }

    protected void executeDocuments(Set<String> components, Set<String> dataformats, Set<String> languages, Set<String> others) {
        // lets use sorted set/maps
        Set<Path> adocFiles = new TreeSet<>();
        Set<Path> missingAdocFiles = new TreeSet<>();
        Set<Path> duplicateAdocFiles;

        // find all camel maven modules
        try (Stream<Path> stream = Stream.concat(
                list(componentsDir.toPath())
                        .filter(dir -> !dir.getFileName().startsWith(".") && !"target".equals(dir.getFileName().toString()))
                        .flatMap(p -> getComponentPath(p).stream()),
                Stream.of(coreDir.toPath(), languagesDir.toPath()))) {
            stream
                    .forEach(dir -> {
                        try (Stream<Path> pathStream = PackageHelper.walk(dir.resolve("src/main/docs"))
                                .filter(f -> f.getFileName().toString().endsWith(".adoc"))) {
                            List<Path> l = pathStream
                                    .collect(Collectors.toList());

                            if (l.isEmpty()) {
                                String n = dir.getFileName().toString();
                                boolean isDir = dir.toFile().isDirectory();
                                boolean valid = isDir && !n.startsWith(".") && !n.endsWith("-base") && !n.endsWith("-common") && !n.equals("src");
                                if (valid) {
                                    missingAdocFiles.add(dir);
                                }
                            } else {
                                adocFiles.addAll(l);
                            }
                        }
                    });
        }

        getLog().info("Found " + adocFiles.size() + " ascii document files");

        // Check duplicates
        duplicateAdocFiles = getDuplicates(adocFiles);
        Set<String> docNames = adocFiles.stream().map(PrepareCatalogMojo::asComponentName).collect(Collectors.toCollection(TreeSet::new));
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
            printMissingWarning(missing);
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
            printMissingWarning(missing);
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
            printMissingWarning(missing);
        }
        missing.clear();

        for (String other : others) {

            if (!docs.contains(other)) {
                missing.add(other);
            }
        }
        if (!missing.isEmpty()) {
            getLog().info("");
            getLog().warn("\tMissing .adoc other documentation: " + missing.size());
            printMissingWarning(missing);
        }
        missing.clear();

        getLog().info("");
        getLog().info("================================================================================");
    }

    private void printMissingWarning(List<String> missing) {
        for (String name : missing) {
            getLog().warn("\t\t" + name);
        }
    }

    private void printModelsReport(Set<Path> json, Set<Path> duplicate, Set<Path> missingLabels, Map<String, Set<String>> usedLabels, Set<Path> missingJavaDoc) {
        getLog().info("================================================================================");

        getLog().info("");
        getLog().info("Camel model catalog report");
        getLog().info("");
        getLog().info("\tModels found: " + json.size());
        printComponentDebug(json);
        if (!duplicate.isEmpty()) {
            getLog().info("");
            getLog().warn("\tDuplicate models detected: " + duplicate.size());
            printComponentWarning(duplicate);
        }
        if (!missingLabels.isEmpty()) {
            getLog().info("");
            getLog().warn("\tMissing labels detected: " + missingLabels.size());
            printComponentWarning(missingLabels);
        }
        printUsedLabels(usedLabels);
        if (!missingJavaDoc.isEmpty()) {
            getLog().info("");
            getLog().warn("\tMissing javadoc on models: " + missingJavaDoc.size());
            printComponentWarning(missingJavaDoc);
        }
        getLog().info("");
        getLog().info("================================================================================");
    }

    private void printComponentWarning(Set<Path> duplicate) {
        for (Path file : duplicate) {
            getLog().warn("\t\t" + asComponentName(file));
        }
    }

    private void printComponentDebug(Set<Path> json) {
        if (getLog().isDebugEnabled()) {
            for (Path file : json) {
                getLog().debug("\t\t" + asComponentName(file));
            }
        }
    }

    private void printUsedLabels(Map<String, Set<String>> usedLabels) {
        if (getLog().isDebugEnabled()) {
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
        }
    }

    private void printComponentsReport(Set<Path> json, Set<Path> duplicate, Set<Path> missing, Map<String, Set<String>> usedComponentLabels, Set<String> usedOptionsLabels,
                                       Set<String> unusedLabels, Set<Path> missingFirstVersions) {
        getLog().info("================================================================================");
        getLog().info("");
        getLog().info("Camel component catalog report");
        getLog().info("");
        getLog().info("\tComponents found: " + json.size());
        printComponentDebug(json);
        if (!duplicate.isEmpty()) {
            getLog().info("");
            getLog().warn("\tDuplicate components detected: " + duplicate.size());
            printComponentWarning(duplicate);
        }
        if (getLog().isDebugEnabled()) {
            if (!usedComponentLabels.isEmpty()) {
                getLog().info("");
                getLog().info("\tUsed component labels: " + usedComponentLabels.size());
                for (Map.Entry<String, Set<String>> entry : usedComponentLabels.entrySet()) {
                    getLog().debug("\t\t" + entry.getKey() + ":");
                    for (String name : entry.getValue()) {
                        getLog().debug("\t\t\t" + name);
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
            printWarnings(missing);
        }
        if (!missingFirstVersions.isEmpty()) {
            getLog().info("");
            getLog().warn("\tComponents without firstVersion defined: " + missingFirstVersions.size());
            printWarnings(missingFirstVersions);
        }
        getLog().info("");
        getLog().info("================================================================================");
    }

    private void printWarnings(Set<Path> missing) {
        for (Path name : missing) {
            getLog().warn("\t\t" + name.getFileName().toString());
        }
    }

    private void printDataFormatsReport(Set<Path> json, Set<Path> duplicate, Map<String, Set<String>> usedLabels, Set<Path> missingFirstVersions) {
        getLog().info("================================================================================");
        getLog().info("");
        getLog().info("Camel data format catalog report");
        getLog().info("");
        getLog().info("\tDataFormats found: " + json.size());
        printComponentDebug(json);
        if (!duplicate.isEmpty()) {
            getLog().info("");
            getLog().warn("\tDuplicate dataformat detected: " + duplicate.size());
            printComponentWarning(duplicate);
        }
        printUsedLabels(usedLabels);
        if (!missingFirstVersions.isEmpty()) {
            getLog().info("");
            getLog().warn("\tDataFormats without firstVersion defined: " + missingFirstVersions.size());
            printWarnings(missingFirstVersions);
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
        printComponentDebug(json);
        if (!duplicate.isEmpty()) {
            getLog().info("");
            getLog().warn("\tDuplicate language detected: " + duplicate.size());
            printComponentWarning(duplicate);
        }
        printUsedLabels(usedLabels);
        if (!missingFirstVersions.isEmpty()) {
            getLog().info("");
            getLog().warn("\tLanguages without firstVersion defined: " + missingFirstVersions.size());
            printWarnings(missingFirstVersions);
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
        printComponentDebug(json);
        if (!duplicate.isEmpty()) {
            getLog().info("");
            getLog().warn("\tDuplicate other detected: " + duplicate.size());
            printComponentWarning(duplicate);
        }
        printUsedLabels(usedLabels);
        if (!missingFirstVersions.isEmpty()) {
            getLog().info("");
            getLog().warn("\tOthers without firstVersion defined: " + missingFirstVersions.size());
            printWarnings(missingFirstVersions);
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
        printComponentDebug(docs);
        if (!duplicate.isEmpty()) {
            getLog().info("");
            getLog().warn("\tDuplicate document detected: " + duplicate.size());
            printComponentWarning(duplicate);
        }
        getLog().info("");
        if (!missing.isEmpty()) {
            getLog().info("");
            getLog().warn("\tMissing document detected: " + missing.size());
            printWarnings(missing);
        }
        getLog().info("");
        getLog().info("================================================================================");
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
                if (af.isRegularFile() && at.isRegularFile() && af.size() == at.size()
                        && af.lastModifiedTime().compareTo(at.lastAccessTime()) < 0) {
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

}
