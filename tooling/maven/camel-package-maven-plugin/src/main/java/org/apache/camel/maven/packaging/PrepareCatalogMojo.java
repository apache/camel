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
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.tooling.util.FileUtil;
import org.apache.camel.tooling.util.JSonSchemaHelper;
import org.apache.camel.tooling.util.PackageHelper;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.asciidoctor.Asciidoctor;
import org.asciidoctor.OptionsBuilder;

/**
 * Prepares the camel catalog to include component, data format, and eip
 * descriptors, and generates a report.
 */
@Mojo(name = "prepare-catalog", threadSafe = true)
public class PrepareCatalogMojo extends AbstractMojo {

    private static final String[] EXCLUDE_DOC_FILES = {"camel-core-osgi", "camel-core-xml", "camel-http-common", "camel-http-base", "camel-jetty-common", "camel-debezium-common"};

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
    private MavenProjectHelper projectHelper;

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
        executeModel();
        Set<String> components = executeComponents();
        Set<String> dataformats = executeDataFormats();
        Set<String> languages = executeLanguages();
        Set<String> others = executeOthers();
        executeDocuments(components, dataformats, languages, others);
        executeArchetypes();
        executeXmlSchemas();
        executeMain();
    }

    protected void executeModel() throws MojoExecutionException, MojoFailureException {
        getLog().info("================================================================================");
        getLog().info("Copying all Camel model json descriptors");

        // lets use sorted set/maps
        Set<File> jsonFiles = new TreeSet<>();
        Set<File> duplicateJsonFiles = new TreeSet<>();
        Set<File> missingLabels = new TreeSet<>();
        Set<File> missingJavaDoc = new TreeSet<>();
        Map<String, Set<String>> usedLabels = new TreeMap<>();

        // find all json files in camel-core
        if (coreDir != null && coreDir.isDirectory()) {
            File target = new File(coreDir, "target/classes/org/apache/camel/model");
            PackageHelper.findJsonFiles(target, jsonFiles);
        }

        // find all json files in camel-spring
        if (springDir != null && springDir.isDirectory()) {
            File target = new File(springDir, "target/classes/org/apache/camel/spring");
            PackageHelper.findJsonFiles(target, jsonFiles);
            File target2 = new File(springDir, "target/classes/org/apache/camel/core/xml");
            PackageHelper.findJsonFiles(target2, jsonFiles);
        }

        getLog().info("Found " + jsonFiles.size() + " model json files");

        // make sure to create out dir
        modelsOutDir.mkdirs();
        // we only want to warn for duplicates if its a clean build
        boolean warnDups = modelsOutDir.list() == null || modelsOutDir.list().length == 0;

        for (File file : jsonFiles) {
            File to = new File(modelsOutDir, file.getName());
            if (to.exists()) {
                if (warnDups) {
                    duplicateJsonFiles.add(to);
                    getLog().warn("Duplicate model name detected: " + to);
                } else if (file.lastModified() < to.lastModified()) {
                    getLog().debug("Skipping generated file: " + to);
                    continue;
                } else {
                    getLog().warn("Stale file: " + to);
                }
            }
            try {
                copyFile(file, to);
            } catch (IOException e) {
                throw new MojoFailureException("Cannot copy file from " + file + " -> " + to, e);
            }

            try {
                // check if we have a label as we want the eip to include labels
                String text = PackageHelper.loadText(file);
                // just do a basic label check
                if (text.contains("\"label\": \"\"")) {
                    missingLabels.add(file);
                } else {
                    String name = asComponentName(file);
                    Matcher matcher = LABEL_PATTERN.matcher(text);
                    // grab the label, and remember it in the used labels
                    if (matcher.find()) {
                        String label = matcher.group(1);
                        String[] labels = label.split(",");
                        for (String s : labels) {
                            usedLabels.computeIfAbsent(s, k -> new TreeSet<>()).add(name);
                        }
                    }
                }

                // check all the properties if they have description
                List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("properties", text, true);
                for (Map<String, String> row : rows) {
                    String name = row.get("name");
                    // skip checking these as they have no documentation
                    if ("outputs".equals(name) || "transforms".equals(name)) {
                        continue;
                    }

                    String doc = row.get("description");
                    if (doc == null || doc.isEmpty()) {
                        missingJavaDoc.add(file);
                        break;
                    }
                }
            } catch (IOException e) {
                // ignore
            }
        }

        File all = new File(modelsOutDir, "../models.properties");
        try {
            FileOutputStream fos = new FileOutputStream(all, false);

            String[] names = modelsOutDir.list();
            List<String> models = new ArrayList<>();
            // sort the names
            for (String name : names) {
                if (name.endsWith(PackageHelper.JSON_SUFIX)) {
                    // strip out .json from the name
                    String modelName = name.substring(0, name.length() - 5);
                    models.add(modelName);
                }
            }

            Collections.sort(models);
            for (String name : models) {
                fos.write(name.getBytes());
                fos.write("\n".getBytes());
            }

            fos.close();

        } catch (IOException e) {
            throw new MojoFailureException("Error writing to file " + all);
        }

        printModelsReport(jsonFiles, duplicateJsonFiles, missingLabels, usedLabels, missingJavaDoc);
    }

    // CHECKSTYLE:OFF
    protected Set<String> executeComponents() throws MojoExecutionException, MojoFailureException {
        getLog().info("Copying all Camel component json descriptors");

        // lets use sorted set/maps
        Set<File> jsonFiles = new TreeSet<>();
        Set<File> duplicateJsonFiles = new TreeSet<>();
        Set<File> componentFiles = new TreeSet<>();
        Set<File> missingComponents = new TreeSet<>();
        Map<String, Set<String>> usedComponentLabels = new TreeMap<>();
        Set<String> usedOptionLabels = new TreeSet<>();
        Set<String> unlabeledOptions = new TreeSet<>();
        Set<File> missingFirstVersions = new TreeSet<>();

        // find all json files in components and camel-core
        if (componentsDir != null && componentsDir.isDirectory()) {
            File[] components = componentsDir.listFiles();
            if (components != null) {
                for (File dir : components) {
                    if (dir.isDirectory() && !"target".equals(dir.getName())) {
                        File target = new File(dir, "target/classes");

                        // special for these as they are in sub dir
                        if ("camel-as2".equals(dir.getName())) {
                            target = new File(dir, "camel-as2-component/target/classes");
                        } else if ("camel-salesforce".equals(dir.getName())) {
                            target = new File(dir, "camel-salesforce-component/target/classes");
                        } else if ("camel-olingo2".equals(dir.getName())) {
                            target = new File(dir, "camel-olingo2-component/target/classes");
                        } else if ("camel-olingo4".equals(dir.getName())) {
                            target = new File(dir, "camel-olingo4-component/target/classes");
                        } else if ("camel-box".equals(dir.getName())) {
                            target = new File(dir, "camel-box-component/target/classes");
                        } else if ("camel-servicenow".equals(dir.getName())) {
                            target = new File(dir, "camel-servicenow-component/target/classes");
                        } else if ("camel-fhir".equals(dir.getName())) {
                            target = new File(dir, "camel-fhir-component/target/classes");
                        } else {
                            // this module must be active with a source folder
                            File src = new File(dir, "src");
                            boolean active = src.isDirectory() && src.exists();
                            if (!active) {
                                continue;
                            }
                        }

                        int before = componentFiles.size();
                        int before2 = jsonFiles.size();

                        findComponentFilesRecursive(target, jsonFiles, componentFiles, new CamelComponentsFileFilter());

                        int after = componentFiles.size();
                        int after2 = jsonFiles.size();
                        if (before != after && before2 == after2) {
                            missingComponents.add(dir);
                        }
                    }
                }
            }
        }
        if (coreDir != null && coreDir.isDirectory()) {
            File target = new File(coreDir, "target/classes");

            int before = componentFiles.size();
            int before2 = jsonFiles.size();

            findComponentFilesRecursive(target, jsonFiles, componentFiles, new CamelComponentsFileFilter());

            int after = componentFiles.size();
            int after2 = jsonFiles.size();
            if (before != after && before2 == after2) {
                missingComponents.add(coreDir);
            }
        }

        getLog().info("Found " + componentFiles.size() + " component.properties files");
        getLog().info("Found " + jsonFiles.size() + " component json files");

        // make sure to create out dir
        componentsOutDir.mkdirs();
        // we only want to warn for duplicates if its a clean build
        boolean warnDups = componentsOutDir.list() == null || componentsOutDir.list().length == 0;

        Set<String> alternativeSchemes = new HashSet<>();

        for (File file : jsonFiles) {
            File to = new File(componentsOutDir, file.getName());
            if (to.exists()) {
                if (warnDups) {
                    duplicateJsonFiles.add(to);
                    getLog().warn("Duplicate component name detected: " + to);
                } else if (file.lastModified() < to.lastModified()) {
                    getLog().debug("Skipping generated file: " + to);
                    continue;
                } else {
                    getLog().warn("Stale file: " + to);
                }
            }
            try {
                copyFile(file, to);
            } catch (IOException e) {
                throw new MojoFailureException("Cannot copy file from " + file + " -> " + to, e);
            }

            // check if we have a component label as we want the components to
            // include labels
            try {
                String text = PackageHelper.loadText(file);
                String name = asComponentName(file);
                Matcher matcher = LABEL_PATTERN.matcher(text);
                // grab the label, and remember it in the used labels
                if (matcher.find()) {
                    String label = matcher.group(1);
                    String[] labels = label.split(",");
                    for (String s : labels) {
                        Set<String> components = usedComponentLabels.computeIfAbsent(s, k -> new TreeSet<>());
                        components.add(name);
                    }
                }

                // check all the component options and grab the label(s) they
                // use
                List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("componentProperties", text, true);
                for (Map<String, String> row : rows) {
                    String label = row.get("label");

                    if (label != null && !label.isEmpty()) {
                        String[] parts = label.split(",");
                        Collections.addAll(usedOptionLabels, parts);
                    }
                }

                // check all the endpoint options and grab the label(s) they use
                int unused = 0;
                rows = JSonSchemaHelper.parseJsonSchema("properties", text, true);
                for (Map<String, String> row : rows) {
                    String label = row.get("label");
                    if (label != null && !label.isEmpty()) {
                        String[] parts = label.split(",");
                        usedOptionLabels.addAll(Arrays.asList(parts));
                    } else {
                        unused++;
                    }
                }

                if (unused >= UNUSED_LABELS_WARN) {
                    unlabeledOptions.add(name);
                }

                // remember alternative schemes
                rows = JSonSchemaHelper.parseJsonSchema("component", text, false);
                for (Map<String, String> row : rows) {
                    String alternativeScheme = row.get("alternativeSchemes");
                    if (alternativeScheme != null && !alternativeScheme.isEmpty()) {
                        String[] parts = alternativeScheme.split(",");
                        // skip first as that is the regular scheme
                        alternativeSchemes.addAll(Arrays.asList(parts).subList(1, parts.length));
                    }
                }

                // detect missing first version
                String firstVersion = null;
                for (Map<String, String> row : rows) {
                    if (row.get("firstVersion") != null) {
                        firstVersion = row.get("firstVersion");
                    }
                }
                if (firstVersion == null) {
                    missingFirstVersions.add(file);
                }

            } catch (IOException e) {
                // ignore
            }
        }

        Set<String> componentNames = generateJsonList(componentsOutDir.toPath(), "../components.properties");

        printComponentsReport(jsonFiles, duplicateJsonFiles, missingComponents, usedComponentLabels, usedOptionLabels, unlabeledOptions, missingFirstVersions);

        // filter out duplicate component names that are alternative scheme
        // names
        componentNames.removeAll(alternativeSchemes);
        return componentNames;
    }
    // CHECKSTYLE:ON

    protected Set<String> executeDataFormats() throws MojoExecutionException, MojoFailureException {
        getLog().info("Copying all Camel dataformat json descriptors");

        // lets use sorted set/maps
        Set<File> jsonFiles = new TreeSet<>();
        Set<File> duplicateJsonFiles = new TreeSet<>();
        Set<File> dataFormatFiles = new TreeSet<>();
        Map<String, Set<String>> usedLabels = new TreeMap<>();
        Set<File> missingFirstVersions = new TreeSet<>();

        // find all data formats from the components directory
        if (componentsDir != null && componentsDir.isDirectory()) {
            File[] dataFormats = componentsDir.listFiles();
            if (dataFormats != null) {
                for (File dir : dataFormats) {
                    // special for this as the data format is in the sub dir
                    if (dir.isDirectory() && "camel-fhir".equals(dir.getName())) {
                        dir = new File(dir, "camel-fhir-component");
                    }
                    if (dir.isDirectory() && !"target".equals(dir.getName())) {
                        File target = new File(dir, "target/classes");
                        // this module must be active with a source folder
                        File src = new File(dir, "src");
                        boolean active = src.isDirectory() && src.exists();
                        if (active) {
                            findDataFormatFilesRecursive(target, jsonFiles, dataFormatFiles, new CamelDataFormatsFileFilter());
                        }
                    }
                }
            }
        }
        if (coreDir != null && coreDir.isDirectory()) {
            File target = new File(coreDir, "target/classes");
            findDataFormatFilesRecursive(target, jsonFiles, dataFormatFiles, new CamelDataFormatsFileFilter());
        }

        getLog().info("Found " + dataFormatFiles.size() + " dataformat.properties files");
        getLog().info("Found " + jsonFiles.size() + " dataformat json files");

        // make sure to create out dir
        dataFormatsOutDir.mkdirs();
        // we only want to warn for duplicates if its a clean build
        boolean warnDups = dataFormatsOutDir.list() == null || dataFormatsOutDir.list().length == 0;

        for (File file : jsonFiles) {
            File to = new File(dataFormatsOutDir, file.getName());
            if (to.exists()) {
                if (warnDups) {
                    duplicateJsonFiles.add(to);
                    getLog().warn("Duplicate dataformat name detected: " + to);
                } else if (file.lastModified() < to.lastModified()) {
                    getLog().debug("Skipping generated file: " + to);
                    continue;
                } else {
                    getLog().warn("Stale file: " + to);
                }
            }
            try {
                copyFile(file, to);
            } catch (IOException e) {
                throw new MojoFailureException("Cannot copy file from " + file + " -> " + to, e);
            }

            // check if we have a label as we want the data format to include
            // labels
            try {
                String text = PackageHelper.loadText(file);
                String name = asComponentName(file);
                Matcher matcher = LABEL_PATTERN.matcher(text);
                // grab the label, and remember it in the used labels
                if (matcher.find()) {
                    String label = matcher.group(1);
                    String[] labels = label.split(",");
                    for (String s : labels) {
                        Set<String> dataFormats = usedLabels.computeIfAbsent(s, k -> new TreeSet<>());
                        dataFormats.add(name);
                    }
                }

                // detect missing first version
                List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("dataformat", text, false);
                String firstVersion = null;
                for (Map<String, String> row : rows) {
                    if (row.get("firstVersion") != null) {
                        firstVersion = row.get("firstVersion");
                    }
                }
                if (firstVersion == null) {
                    missingFirstVersions.add(file);
                }

            } catch (IOException e) {
                // ignore
            }
        }

        Set<String> answer = generateJsonList(dataFormatsOutDir.toPath(), "../dataformats.properties");

        printDataFormatsReport(jsonFiles, duplicateJsonFiles, usedLabels, missingFirstVersions);

        return answer;
    }

    protected Set<String> executeLanguages() throws MojoExecutionException, MojoFailureException {
        getLog().info("Copying all Camel language json descriptors");

        // lets use sorted set/maps
        Set<File> jsonFiles = new TreeSet<>();
        Set<File> duplicateJsonFiles = new TreeSet<>();
        Set<File> languageFiles = new TreeSet<>();
        Map<String, Set<String>> usedLabels = new TreeMap<>();
        Set<File> missingFirstVersions = new TreeSet<>();

        // find all languages from the components directory
        if (componentsDir != null && componentsDir.isDirectory()) {
            File[] languages = componentsDir.listFiles();
            if (languages != null) {
                for (File dir : languages) {
                    if (dir.isDirectory() && !"target".equals(dir.getName())) {
                        File target = new File(dir, "target/classes");
                        // this module must be active with a source folder
                        File src = new File(dir, "src");
                        boolean active = src.isDirectory() && src.exists();
                        if (active) {
                            findLanguageFilesRecursive(target, jsonFiles, languageFiles, new CamelLanguagesFileFilter());
                        }
                    }
                }
            }
        }
        if (baseDir != null && baseDir.isDirectory()) {
            File target = new File(baseDir, "target/classes");
            findLanguageFilesRecursive(target, jsonFiles, languageFiles, new CamelLanguagesFileFilter());
            // also look in camel-jaxp
            target = new File(baseDir, "../camel-jaxp/target/classes");
            findLanguageFilesRecursive(target, jsonFiles, languageFiles, new CamelLanguagesFileFilter());
        }

        getLog().info("Found " + languageFiles.size() + " language.properties files");
        getLog().info("Found " + jsonFiles.size() + " language json files");

        // make sure to create out dir
        languagesOutDir.mkdirs();
        // we only want to warn for duplicates if its a clean build
        boolean warnDups = languagesOutDir.list() == null || languagesOutDir.list().length == 0;

        for (File file : jsonFiles) {
            File to = new File(languagesOutDir, file.getName());
            if (to.exists()) {
                if (warnDups) {
                    duplicateJsonFiles.add(to);
                    getLog().warn("Duplicate language name detected: " + to);
                } else if (file.lastModified() < to.lastModified()) {
                    getLog().debug("Skipping generated file: " + to);
                    continue;
                } else {
                    getLog().warn("Stale file: " + to);
                }
            }
            try {
                copyFile(file, to);
            } catch (IOException e) {
                throw new MojoFailureException("Cannot copy file from " + file + " -> " + to, e);
            }

            // check if we have a label as we want the data format to include
            // labels
            try {
                String text = PackageHelper.loadText(file);
                String name = asComponentName(file);
                Matcher matcher = LABEL_PATTERN.matcher(text);
                // grab the label, and remember it in the used labels
                if (matcher.find()) {
                    String label = matcher.group(1);
                    String[] labels = label.split(",");
                    for (String s : labels) {
                        Set<String> languages = usedLabels.computeIfAbsent(s, k -> new TreeSet<>());
                        languages.add(name);
                    }
                }

                // detect missing first version
                List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("language", text, false);
                String firstVersion = null;
                for (Map<String, String> row : rows) {
                    if (row.get("firstVersion") != null) {
                        firstVersion = row.get("firstVersion");
                    }
                }
                if (firstVersion == null) {
                    missingFirstVersions.add(file);
                }

            } catch (IOException e) {
                // ignore
            }
        }

        Set<String> answer = generateJsonList(languagesOutDir.toPath(), "../languages.properties");

        printLanguagesReport(jsonFiles, duplicateJsonFiles, usedLabels, missingFirstVersions);

        return answer;
    }

    private Set<String> executeOthers() throws MojoFailureException {
        getLog().info("Copying all Camel other json descriptors");

        // lets use sorted set/maps
        Set<File> jsonFiles = new TreeSet<>();
        Set<File> duplicateJsonFiles = new TreeSet<>();
        Set<File> otherFiles = new TreeSet<>();
        Map<String, Set<String>> usedLabels = new TreeMap<>();
        Set<File> missingFirstVersions = new TreeSet<>();

        // find all others from the components directory
        if (componentsDir != null && componentsDir.isDirectory()) {
            File[] others = componentsDir.listFiles();
            if (others != null) {
                for (File dir : others) {

                    // skip these special cases
                    boolean special = "camel-core-osgi".equals(dir.getName()) || "camel-core-xml".equals(dir.getName()) || "camel-box".equals(dir.getName())
                                      || "camel-http-base".equals(dir.getName()) || "camel-http-common".equals(dir.getName()) || "camel-jetty-common".equals(dir.getName());
                    boolean special2 = "camel-as2".equals(dir.getName()) || "camel-olingo2".equals(dir.getName()) || "camel-olingo4".equals(dir.getName())
                                       || "camel-servicenow".equals(dir.getName()) || "camel-salesforce".equals(dir.getName()) || "camel-fhir".equals(dir.getName());
                    boolean special3 = "camel-debezium-common".equals(dir.getName());
                    if (special || special2 || special3) {
                        continue;
                    }

                    if (dir.isDirectory() && !"target".equals(dir.getName())) {
                        File target = new File(dir, "target/classes");
                        if (target.exists()) {
                            // this module must be active with a source folder
                            File src = new File(dir, "src");
                            boolean active = src.isDirectory() && src.exists();
                            if (active) {
                                findOtherFilesRecursive(target, jsonFiles, otherFiles, new CamelOthersFileFilter());
                            }
                        }
                    }
                }
            }
        }
        // nothing in camel-core

        getLog().info("Found " + otherFiles.size() + " other.properties files");
        getLog().info("Found " + jsonFiles.size() + " other json files");

        // make sure to create out dir
        othersOutDir.mkdirs();
        // we only want to warn for duplicates if its a clean build
        boolean warnDups = othersOutDir.list() == null || othersOutDir.list().length == 0;

        for (File file : jsonFiles) {
            File to = new File(othersOutDir, file.getName());
            if (to.exists()) {
                if (warnDups) {
                    duplicateJsonFiles.add(to);
                    getLog().warn("Duplicate other name detected: " + to);
                } else if (file.lastModified() < to.lastModified()) {
                    getLog().debug("Skipping generated file: " + to);
                    continue;
                } else {
                    getLog().warn("Stale file: " + to);
                }
            }
            try {
                copyFile(file, to);
            } catch (IOException e) {
                throw new MojoFailureException("Cannot copy file from " + file + " -> " + to, e);
            }

            // check if we have a label as we want the other to include labels
            try {
                String text = PackageHelper.loadText(file);
                String name = asComponentName(file);
                Matcher matcher = LABEL_PATTERN.matcher(text);
                // grab the label, and remember it in the used labels
                if (matcher.find()) {
                    String label = matcher.group(1);
                    String[] labels = label.split(",");
                    for (String s : labels) {
                        Set<String> others = usedLabels.computeIfAbsent(s, k -> new TreeSet<>());
                        others.add(name);
                    }
                }

                // detect missing first version
                List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("other", text, false);
                String firstVersion = null;
                for (Map<String, String> row : rows) {
                    if (row.get("firstVersion") != null) {
                        firstVersion = row.get("firstVersion");
                    }
                }
                if (firstVersion == null) {
                    missingFirstVersions.add(file);
                }

            } catch (IOException e) {
                // ignore
            }
        }

        Set<String> answer = generateJsonList(othersOutDir.toPath(), "../others.properties");

        printOthersReport(jsonFiles, duplicateJsonFiles, usedLabels, missingFirstVersions);

        return answer;
    }

    protected void executeArchetypes() throws MojoExecutionException, MojoFailureException {
        getLog().info("Copying Archetype Catalog");

        // find the generate catalog
        File file = new File(archetypesDir, "target/classes/archetype-catalog.xml");

        // make sure to create out dir
        archetypesOutDir.mkdirs();

        if (file.exists() && file.isFile()) {
            File to = new File(archetypesOutDir, file.getName());
            try {
                copyFile(file, to);
            } catch (IOException e) {
                throw new MojoFailureException("Cannot copy file from " + file + " -> " + to, e);
            }
        }
    }

    protected void executeXmlSchemas() throws MojoExecutionException, MojoFailureException {
        getLog().info("Copying Spring/Blueprint XML schemas");

        schemasOutDir.mkdirs();

        File file = new File(springSchemaDir, "camel-spring.xsd");
        if (file.exists() && file.isFile()) {
            File to = new File(schemasOutDir, file.getName());
            try {
                copyFile(file, to);
            } catch (IOException e) {
                throw new MojoFailureException("Cannot copy file from " + file + " -> " + to, e);
            }
        }
        file = new File(blueprintSchemaDir, "camel-blueprint.xsd");
        if (file.exists() && file.isFile()) {
            File to = new File(schemasOutDir, file.getName());
            try {
                copyFile(file, to);
            } catch (IOException e) {
                throw new MojoFailureException("Cannot copy file from " + file + " -> " + to, e);
            }
        }
    }

    protected void executeMain() throws MojoExecutionException, MojoFailureException {
        getLog().info("Copying camel-main metadata");

        mainOutDir.mkdirs();

        File file = new File(mainDir, "camel-main-configuration-metadata.json");
        if (file.exists() && file.isFile()) {
            File to = new File(mainOutDir, file.getName());
            try {
                copyFile(file, to);
            } catch (IOException e) {
                throw new MojoFailureException("Cannot copy file from " + file + " -> " + to, e);
            }
        }
    }

    protected void executeDocuments(Set<String> components, Set<String> dataformats, Set<String> languages, Set<String> others)
        throws MojoExecutionException, MojoFailureException {
        getLog().info("Copying all Camel documents (ascii docs)");

        // lets use sorted set/maps
        Set<File> adocFiles = new TreeSet<>();
        Set<File> missingAdocFiles = new TreeSet<>();
        Set<File> duplicateAdocFiles = new TreeSet<>();

        // find all camel maven modules
        if (componentsDir != null && componentsDir.isDirectory()) {
            File[] componentFiles = componentsDir.listFiles();
            if (componentFiles != null) {
                for (File dir : componentFiles) {
                    if (dir.isDirectory() && !"target".equals(dir.getName()) && !dir.getName().startsWith(".") && !excludeDocumentDir(dir.getName())) {
                        File target = new File(dir, "src/main/docs");

                        // special for these as they are in sub dir
                        if ("camel-as2".equals(dir.getName())) {
                            target = new File(dir, "camel-as2-component/src/main/docs");
                        } else if ("camel-salesforce".equals(dir.getName())) {
                            target = new File(dir, "camel-salesforce-component/src/main/docs");
                        } else if ("camel-olingo2".equals(dir.getName())) {
                            target = new File(dir, "camel-olingo2-component/src/main/docs");
                        } else if ("camel-olingo4".equals(dir.getName())) {
                            target = new File(dir, "camel-olingo4-component/src/main/docs");
                        } else if ("camel-box".equals(dir.getName())) {
                            target = new File(dir, "camel-box-component/src/main/docs");
                        } else if ("camel-servicenow".equals(dir.getName())) {
                            target = new File(dir, "camel-servicenow-component/src/main/docs");
                        } else if ("camel-fhir".equals(dir.getName())) {
                            target = new File(dir, "camel-fhir-component/src/main/docs");
                        } else {
                            // this module must be active with a source folder
                            File src = new File(dir, "src");
                            boolean active = src.isDirectory() && src.exists();
                            if (!active) {
                                continue;
                            }
                        }

                        int before = adocFiles.size();
                        findAsciiDocFilesRecursive(target, adocFiles, new CamelAsciiDocFileFilter());
                        int after = adocFiles.size();

                        if (before == after) {
                            missingAdocFiles.add(dir);
                        }
                    }
                }
            }
        }
        if (coreDir != null && coreDir.isDirectory()) {
            File target = new File(coreDir, "src/main/docs");
            findAsciiDocFilesRecursive(target, adocFiles, new CamelAsciiDocFileFilter());
        }
        if (baseDir != null && baseDir.isDirectory()) {
            File target = new File(baseDir, "src/main/docs");
            findAsciiDocFilesRecursive(target, adocFiles, new CamelAsciiDocFileFilter());
            // also look in camel-jaxp
            target = new File(coreDir, "../camel-jaxp/src/main/docs");
            findAsciiDocFilesRecursive(target, adocFiles, new CamelAsciiDocFileFilter());
        }

        getLog().info("Found " + adocFiles.size() + " ascii document files");

        // make sure to create out dir
        documentsOutDir.mkdirs();
        // we only want to warn for duplicates if its a clean build
        boolean warnDups = documentsOutDir.list() == null || documentsOutDir.list().length == 0;

        // use ascii doctor to convert the adoc files to html so we have
        // documentation in this format as well
        Asciidoctor asciidoctor = Asciidoctor.Factory.create();

        int converted = 0;

        for (File file : adocFiles) {
            File to = new File(documentsOutDir, file.getName());
            if (to.exists()) {
                if (warnDups) {
                    duplicateAdocFiles.add(to);
                    getLog().warn("Duplicate document name detected: " + to);
                } else if (file.lastModified() < to.lastModified()) {
                    getLog().debug("Skipping generated file: " + to);
                    continue;
                } else {
                    getLog().warn("Stale file: " + to);
                }
            }
            try {
                copyFile(file, to);
            } catch (IOException e) {
                throw new MojoFailureException("Cannot copy file from " + file + " -> " + to, e);
            }

            // convert adoc to html as well
            if (file.getName().endsWith(".adoc")) {
                String newName = file.getName().substring(0, file.getName().length() - 5) + ".html";
                File toHtml = new File(documentsOutDir, newName);

                getLog().debug("Converting ascii document to html -> " + toHtml);
                asciidoctor.convertFile(file, OptionsBuilder.options().toFile(toHtml));

                converted++;

                try {
                    // now fix the html file because we don't want to include
                    // certain lines
                    List<String> lines = FileUtils.readLines(toHtml, Charset.defaultCharset());
                    List<String> output = new ArrayList<>();
                    for (String line : lines) {
                        // skip these lines
                        if (line.contains("% raw %") || line.contains("% endraw %")) {
                            continue;
                        }
                        output.add(line);
                    }
                    if (lines.size() != output.size()) {
                        FileUtils.writeLines(toHtml, output, false);
                    }
                } catch (IOException e) {
                    // ignore
                }
            }
        }

        if (converted > 0) {
            getLog().info("Converted " + converted + " ascii documents to HTML");
        }

        Set<String> docs = new LinkedHashSet<>();

        File all = new File(documentsOutDir, "../docs.properties");
        try {
            FileOutputStream fos = new FileOutputStream(all, false);

            String[] names = documentsOutDir.list();
            List<String> documents = new ArrayList<>();
            // sort the names
            for (String name : names) {
                if (name.endsWith(".adoc")) {
                    // strip out .adoc from the name
                    String documentName = name.substring(0, name.length() - 5);
                    documents.add(documentName);
                }
            }

            Collections.sort(documents);
            for (String name : documents) {
                fos.write(name.getBytes());
                fos.write("\n".getBytes());

                docs.add(name);
            }

            fos.close();

        } catch (IOException e) {
            throw new MojoFailureException("Error writing to file " + all);
        }

        printDocumentsReport(adocFiles, duplicateAdocFiles, missingAdocFiles);

        // find out if we have documents for each component / dataformat /
        // languages / others
        printMissingDocumentsReport(docs, components, dataformats, languages, others);
    }

    private void printMissingDocumentsReport(Set<String> docs, Set<String> components, Set<String> dataformats, Set<String> languages, Set<String> others) {
        getLog().info("");
        getLog().info("Camel missing documents report");
        getLog().info("");

        List<String> missing = new ArrayList<>();
        for (String component : components) {
            // special for mail
            if (component.equals("imap") || component.equals("imaps") || component.equals("pop3") || component.equals("pop3s") || component.equals("smtp")
                || component.equals("smtps")) {
                component = "mail";
            } else if (component.equals("ftp") || component.equals("sftp") || component.equals("ftps")) {
                component = "ftp";
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

    private void printModelsReport(Set<File> json, Set<File> duplicate, Set<File> missingLabels, Map<String, Set<String>> usedLabels, Set<File> missingJavaDoc) {
        getLog().info("================================================================================");

        getLog().info("");
        getLog().info("Camel model catalog report");
        getLog().info("");
        getLog().info("\tModels found: " + json.size());
        for (File file : json) {
            getLog().info("\t\t" + asComponentName(file));
        }
        if (!duplicate.isEmpty()) {
            getLog().info("");
            getLog().warn("\tDuplicate models detected: " + duplicate.size());
            for (File file : duplicate) {
                getLog().warn("\t\t" + asComponentName(file));
            }
        }
        if (!missingLabels.isEmpty()) {
            getLog().info("");
            getLog().warn("\tMissing labels detected: " + missingLabels.size());
            for (File file : missingLabels) {
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
            for (File file : missingJavaDoc) {
                getLog().warn("\t\t" + asComponentName(file));
            }
        }
        getLog().info("");
        getLog().info("================================================================================");
    }

    private void printComponentsReport(Set<File> json, Set<File> duplicate, Set<File> missing, Map<String, Set<String>> usedComponentLabels, Set<String> usedOptionsLabels,
                                       Set<String> unusedLabels, Set<File> missingFirstVersions) {
        getLog().info("================================================================================");
        getLog().info("");
        getLog().info("Camel component catalog report");
        getLog().info("");
        getLog().info("\tComponents found: " + json.size());
        for (File file : json) {
            getLog().info("\t\t" + asComponentName(file));
        }
        if (!duplicate.isEmpty()) {
            getLog().info("");
            getLog().warn("\tDuplicate components detected: " + duplicate.size());
            for (File file : duplicate) {
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
            for (File name : missing) {
                getLog().warn("\t\t" + name.getName());
            }
        }
        if (!missingFirstVersions.isEmpty()) {
            getLog().info("");
            getLog().warn("\tComponents without firstVersion defined: " + missingFirstVersions.size());
            for (File name : missingFirstVersions) {
                getLog().warn("\t\t" + name.getName());
            }
        }
        getLog().info("");
        getLog().info("================================================================================");
    }

    private void printDataFormatsReport(Set<File> json, Set<File> duplicate, Map<String, Set<String>> usedLabels, Set<File> missingFirstVersions) {
        getLog().info("================================================================================");
        getLog().info("");
        getLog().info("Camel data format catalog report");
        getLog().info("");
        getLog().info("\tDataFormats found: " + json.size());
        for (File file : json) {
            getLog().info("\t\t" + asComponentName(file));
        }
        if (!duplicate.isEmpty()) {
            getLog().info("");
            getLog().warn("\tDuplicate dataformat detected: " + duplicate.size());
            for (File file : duplicate) {
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
            for (File name : missingFirstVersions) {
                getLog().warn("\t\t" + name.getName());
            }
        }
        getLog().info("");
        getLog().info("================================================================================");
    }

    private void printLanguagesReport(Set<File> json, Set<File> duplicate, Map<String, Set<String>> usedLabels, Set<File> missingFirstVersions) {
        getLog().info("================================================================================");
        getLog().info("");
        getLog().info("Camel language catalog report");
        getLog().info("");
        getLog().info("\tLanguages found: " + json.size());
        for (File file : json) {
            getLog().info("\t\t" + asComponentName(file));
        }
        if (!duplicate.isEmpty()) {
            getLog().info("");
            getLog().warn("\tDuplicate language detected: " + duplicate.size());
            for (File file : duplicate) {
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
            for (File name : missingFirstVersions) {
                getLog().warn("\t\t" + name.getName());
            }
        }
        getLog().info("");
        getLog().info("================================================================================");
    }

    private void printOthersReport(Set<File> json, Set<File> duplicate, Map<String, Set<String>> usedLabels, Set<File> missingFirstVersions) {
        getLog().info("================================================================================");
        getLog().info("");
        getLog().info("Camel other catalog report");
        getLog().info("");
        getLog().info("\tOthers found: " + json.size());
        for (File file : json) {
            getLog().info("\t\t" + asComponentName(file));
        }
        if (!duplicate.isEmpty()) {
            getLog().info("");
            getLog().warn("\tDuplicate other detected: " + duplicate.size());
            for (File file : duplicate) {
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
            for (File name : missingFirstVersions) {
                getLog().warn("\t\t" + name.getName());
            }
        }
        getLog().info("");
        getLog().info("================================================================================");
    }

    private void printDocumentsReport(Set<File> docs, Set<File> duplicate, Set<File> missing) {
        getLog().info("================================================================================");
        getLog().info("");
        getLog().info("Camel document catalog report");
        getLog().info("");
        getLog().info("\tDocuments found: " + docs.size());
        for (File file : docs) {
            getLog().info("\t\t" + asComponentName(file));
        }
        if (!duplicate.isEmpty()) {
            getLog().info("");
            getLog().warn("\tDuplicate document detected: " + duplicate.size());
            for (File file : duplicate) {
                getLog().warn("\t\t" + asComponentName(file));
            }
        }
        getLog().info("");
        if (!missing.isEmpty()) {
            getLog().info("");
            getLog().warn("\tMissing document detected: " + missing.size());
            for (File name : missing) {
                getLog().warn("\t\t" + name.getName());
            }
        }
        getLog().info("");
        getLog().info("================================================================================");
    }

    private static String asComponentName(File file) {
        String name = file.getName();
        if (name.endsWith(PackageHelper.JSON_SUFIX) || name.endsWith(".adoc")) {
            return name.substring(0, name.length() - 5);
        }
        return name;
    }

    private void findComponentFilesRecursive(File dir, Set<File> found, Set<File> components, FileFilter filter) {
        File[] files = dir.listFiles(filter);
        if (files != null) {
            for (File file : files) {
                // skip files in root dirs as Camel does not store information
                // there but others may do
                boolean rootDir = "classes".equals(dir.getName()) || "META-INF".equals(dir.getName());
                boolean jsonFile = !rootDir && file.isFile() && file.getName().endsWith(PackageHelper.JSON_SUFIX);
                boolean componentFile = !rootDir && file.isFile() && file.getName().equals("component.properties");
                if (jsonFile) {
                    found.add(file);
                } else if (componentFile) {
                    components.add(file);
                } else if (file.isDirectory()) {
                    findComponentFilesRecursive(file, found, components, filter);
                }
            }
        }
    }

    private void findDataFormatFilesRecursive(File dir, Set<File> found, Set<File> dataFormats, FileFilter filter) {
        File[] files = dir.listFiles(filter);
        if (files != null) {
            for (File file : files) {
                // skip files in root dirs as Camel does not store information
                // there but others may do
                boolean rootDir = "classes".equals(dir.getName()) || "META-INF".equals(dir.getName());
                boolean jsonFile = !rootDir && file.isFile() && file.getName().endsWith(PackageHelper.JSON_SUFIX);
                boolean dataFormatFile = !rootDir && file.isFile() && file.getName().equals("dataformat.properties");
                if (jsonFile) {
                    found.add(file);
                } else if (dataFormatFile) {
                    dataFormats.add(file);
                } else if (file.isDirectory()) {
                    findDataFormatFilesRecursive(file, found, dataFormats, filter);
                }
            }
        }
    }

    private void findLanguageFilesRecursive(File dir, Set<File> found, Set<File> languages, FileFilter filter) {
        File[] files = dir.listFiles(filter);
        if (files != null) {
            for (File file : files) {
                // skip files in root dirs as Camel does not store information
                // there but others may do
                boolean rootDir = "classes".equals(dir.getName()) || "META-INF".equals(dir.getName());
                boolean jsonFile = !rootDir && file.isFile() && file.getName().endsWith(PackageHelper.JSON_SUFIX);
                boolean languageFile = !rootDir && file.isFile() && file.getName().equals("language.properties");
                if (jsonFile) {
                    found.add(file);
                } else if (languageFile) {
                    languages.add(file);
                } else if (file.isDirectory()) {
                    findLanguageFilesRecursive(file, found, languages, filter);
                }
            }
        }
    }

    private void findOtherFilesRecursive(File dir, Set<File> found, Set<File> others, FileFilter filter) {
        File[] files = dir.listFiles(filter);
        if (files != null) {
            for (File file : files) {
                // skip files in root dirs as Camel does not store information
                // there but others may do
                boolean rootDir = "classes".equals(dir.getName()) || "META-INF".equals(dir.getName());
                boolean jsonFile = rootDir && file.isFile() && file.getName().endsWith(PackageHelper.JSON_SUFIX);
                boolean otherFile = !rootDir && file.isFile() && file.getName().equals("other.properties");
                if (jsonFile) {
                    found.add(file);
                } else if (otherFile) {
                    others.add(file);
                } else if (file.isDirectory()) {
                    findOtherFilesRecursive(file, found, others, filter);
                }
            }
        }
    }

    private void findAsciiDocFilesRecursive(File dir, Set<File> found, FileFilter filter) {
        File[] files = dir.listFiles(filter);
        if (files != null) {
            for (File file : files) {
                // skip files in root dirs as Camel does not store information
                // there but others may do
                boolean rootDir = "classes".equals(dir.getName()) || "META-INF".equals(dir.getName());
                boolean adocFile = !rootDir && file.isFile() && file.getName().endsWith(".adoc");
                if (adocFile) {
                    found.add(file);
                } else if (file.isDirectory()) {
                    findAsciiDocFilesRecursive(file, found, filter);
                }
            }
        }
    }

    private class CamelComponentsFileFilter implements FileFilter {

        @Override
        public boolean accept(File pathname) {
            if (pathname.isDirectory() && pathname.getName().equals("model")) {
                // do not check the camel-core model packages as there is no
                // components there
                return false;
            }
            if (pathname.isFile() && pathname.getName().endsWith(PackageHelper.JSON_SUFIX)) {
                // must be a components json file
                try {
                    String json = PackageHelper.loadText(pathname);
                    return "component".equals(PackageHelper.getSchemaKind(json));
                } catch (IOException e) {
                    // ignore
                }
            }
            return pathname.isDirectory() || (pathname.isFile() && pathname.getName().equals("component.properties"));
        }
    }

    private class CamelDataFormatsFileFilter implements FileFilter {

        @Override
        public boolean accept(File pathname) {
            if (pathname.isDirectory() && pathname.getName().equals("model")) {
                // do not check the camel-core model packages as there is no
                // components there
                return false;
            }
            if (pathname.isFile() && pathname.getName().endsWith(PackageHelper.JSON_SUFIX)) {
                // must be a dataformat json file
                try {
                    String json = PackageHelper.loadText(pathname);
                    return "dataformat".equals(PackageHelper.getSchemaKind(json));
                } catch (IOException e) {
                    // ignore
                }
            }
            return pathname.isDirectory() || (pathname.isFile() && pathname.getName().equals("dataformat.properties"));
        }
    }

    private class CamelLanguagesFileFilter implements FileFilter {

        @Override
        public boolean accept(File pathname) {
            if (pathname.isDirectory() && pathname.getName().equals("model")) {
                // do not check the camel-core model packages as there is no
                // components there
                return false;
            }
            if (pathname.isFile() && pathname.getName().endsWith(PackageHelper.JSON_SUFIX)) {
                // must be a language json file
                try {
                    String json = PackageHelper.loadText(pathname);
                    return "language".equals(PackageHelper.getSchemaKind(json));
                } catch (IOException e) {
                    // ignore
                }
            }
            return pathname.isDirectory() || (pathname.isFile() && pathname.getName().equals("language.properties"));
        }
    }

    private class CamelOthersFileFilter implements FileFilter {

        @Override
        public boolean accept(File pathname) {
            if (pathname.isFile() && pathname.getName().endsWith(PackageHelper.JSON_SUFIX)) {
                // must be a language json file
                try {
                    String json = PackageHelper.loadText(pathname);
                    return "other".equals(PackageHelper.getSchemaKind(json));
                } catch (IOException e) {
                    // ignore
                }
            }
            return pathname.isDirectory() || (pathname.isFile() && pathname.getName().equals("other.properties"));
        }
    }

    private class CamelAsciiDocFileFilter implements FileFilter {

        @Override
        public boolean accept(File pathname) {
            return pathname.isFile() && pathname.getName().endsWith(".adoc");
        }
    }

    public static Set<String> generateJsonList(Path outDir, String outFile) throws MojoFailureException {
        Set<String> answer;
        Path all = outDir.resolve(outFile);
        try {
            answer = Files.list(outDir).filter(p -> p.getFileName().toString().endsWith(PackageHelper.JSON_SUFIX)).map(p -> p.getFileName().toString())
                // strip out .json from the name
                .map(n -> n.substring(0, n.length() - PackageHelper.JSON_SUFIX.length())).sorted().collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
            String data = String.join("\n", answer) + "\n";
            FileUtil.updateFile(all, data);
            return answer;
        } catch (IOException e) {
            throw new MojoFailureException("Error writing to file " + all);
        }
    }

    public static void copyFile(File from, File to) throws IOException {
        FileUtil.updateFile(from.toPath(), to.toPath());
    }

    private static boolean excludeDocumentDir(String name) {
        for (String exclude : EXCLUDE_DOC_FILES) {
            if (exclude.equals(name)) {
                return true;
            }
        }
        return false;
    }

}
