/**
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

import static org.apache.camel.maven.packaging.PackageHelper.loadText;

/**
 * Prepares the camel catalog to include component, data format, and eip descriptors,
 * and generates a report.
 *
 * @goal prepare-catalog
 */
public class PrepareCatalogMojo extends AbstractMojo {

    public static final int BUFFER_SIZE = 128 * 1024;

    private static final Pattern LABEL_PATTERN = Pattern.compile("\\\"label\\\":\\s\\\"([\\w,]+)\\\"");

    private static final int UNUSED_LABELS_WARN = 15;

    /**
     * The maven project.
     *
     * @parameter property="project"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /**
     * Whether to validate if the components, data formats, and languages are properly documented and have all the needed details.
     *
     * @parameter default-value="true"
     */
    protected Boolean validate;

    /**
     * The output directory for components catalog
     *
     * @parameter default-value="${project.build.directory}/classes/org/apache/camel/catalog/components"
     */
    protected File componentsOutDir;

    /**
     * The output directory for dataformats catalog
     *
     * @parameter default-value="${project.build.directory}/classes/org/apache/camel/catalog/dataformats"
     */
    protected File dataFormatsOutDir;

    /**
     * The output directory for languages catalog
     *
     * @parameter default-value="${project.build.directory}/classes/org/apache/camel/catalog/languages"
     */
    protected File languagesOutDir;

    /**
     * The output directory for documents catalog
     *
     * @parameter default-value="${project.build.directory}/classes/org/apache/camel/catalog/docs"
     */
    protected File documentsOutDir;

    /**
     * The output directory for models catalog
     *
     * @parameter default-value="${project.build.directory}/classes/org/apache/camel/catalog/models"
     */
    protected File modelsOutDir;

    /**
     * The output directory for archetypes catalog
     *
     * @parameter default-value="${project.build.directory}/classes/org/apache/camel/catalog/archetypes"
     */
    protected File archetypesOutDir;

    /**
     * The output directory for XML schemas catalog
     *
     * @parameter default-value="${project.build.directory}/classes/org/apache/camel/catalog/schemas"
     */
    protected File schemasOutDir;

    /**
     * The components directory where all the Apache Camel components are
     *
     * @parameter default-value="${project.build.directory}/../../../components"
     */
    protected File componentsDir;

    /**
     * The camel-core directory where camel-core components are
     *
     * @parameter default-value="${project.build.directory}/../../../camel-core"
     */
    protected File coreDir;

    /**
     * The archetypes directory where all the Apache Camel Maven archetypes are
     *
     * @parameter default-value="${project.build.directory}/../../../tooling/archetypes"
     */
    protected File archetypesDir;

    /**
     * The directory where the camel-spring XML schema are
     *
     * @parameter default-value="${project.build.directory}/../../../components/camel-spring/target/schema"
     */
    protected File springSchemaDir;

    /**
     * The directory where the camel-blueprint XML schema are
     *
     * @parameter default-value="${project.build.directory}/../../../components/camel-blueprint/target/schema"
     */
    protected File blueprintSchemaDir;

    /**
     * Maven ProjectHelper.
     *
     * @component
     * @readonly
     */
    private MavenProjectHelper projectHelper;

    /**
     * Execute goal.
     *
     * @throws org.apache.maven.plugin.MojoExecutionException execution of the main class or one of the
     *                                                        threads it generated failed.
     * @throws org.apache.maven.plugin.MojoFailureException   something bad happened...
     */
    public void execute() throws MojoExecutionException, MojoFailureException {
        executeModel();
        executeComponents();
        executeDataFormats();
        executeLanguages();
        executeDocuments();
        executeArchetypes();
        executeXmlSchemas();
    }

    protected void executeModel() throws MojoExecutionException, MojoFailureException {
        getLog().info("================================================================================");
        getLog().info("Copying all Camel model json descriptors");

        // lets use sorted set/maps
        Set<File> jsonFiles = new TreeSet<File>();
        Set<File> duplicateJsonFiles = new TreeSet<File>();
        Set<File> missingLabels = new TreeSet<File>();
        Set<File> missingJavaDoc = new TreeSet<File>();
        Map<String, Set<String>> usedLabels = new TreeMap<String, Set<String>>();

        // find all json files in camel-core
        if (coreDir != null && coreDir.isDirectory()) {
            File target = new File(coreDir, "target/classes/org/apache/camel/model");
            PackageHelper.findJsonFiles(target, jsonFiles, new PackageHelper.CamelComponentsModelFilter());
        }

        getLog().info("Found " + jsonFiles.size() + " model json files");

        // make sure to create out dir
        modelsOutDir.mkdirs();

        for (File file : jsonFiles) {
            File to = new File(modelsOutDir, file.getName());
            if (to.exists()) {
                duplicateJsonFiles.add(to);
                getLog().warn("Duplicate model name detected: " + to);
            }
            try {
                copyFile(file, to);
            } catch (IOException e) {
                throw new MojoFailureException("Cannot copy file from " + file + " -> " + to, e);
            }

            try {
                // check if we have a label as we want the eip to include labels
                String text = loadText(new FileInputStream(file));
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
                            Set<String> models = usedLabels.get(s);
                            if (models == null) {
                                models = new TreeSet<String>();
                                usedLabels.put(s, models);
                            }
                            models.add(name);
                        }
                    }
                }

                // check all the properties if they have description
                List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("properties", text, true);
                for (Map<String, String> row : rows) {
                    String name = row.get("name");
                    // skip checking these as they have no documentation
                    if ("outputs".equals(name)) {
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
            List<String> models = new ArrayList<String>();
            // sort the names
            for (String name : names) {
                if (name.endsWith(".json")) {
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

    protected void executeComponents() throws MojoExecutionException, MojoFailureException {
        getLog().info("Copying all Camel component json descriptors");

        // lets use sorted set/maps
        Set<File> jsonFiles = new TreeSet<File>();
        Set<File> duplicateJsonFiles = new TreeSet<File>();
        Set<File> componentFiles = new TreeSet<File>();
        Set<File> missingComponents = new TreeSet<File>();
        Map<String, Set<String>> usedComponentLabels = new TreeMap<String, Set<String>>();
        Set<String> usedOptionLabels = new TreeSet<String>();
        Set<String> unlabeledOptions = new TreeSet<String>();

        // find all json files in components and camel-core
        if (componentsDir != null && componentsDir.isDirectory()) {
            File[] components = componentsDir.listFiles();
            if (components != null) {
                for (File dir : components) {
                    // skip camel-jetty8 as its a duplicate of camel-jetty9
                    if (dir.isDirectory() && !"camel-jetty8".equals(dir.getName()) && !"target".equals(dir.getName())) {
                        File target = new File(dir, "target/classes");

                        // special for camel-salesforce which is in a sub dir
                        if ("camel-salesforce".equals(dir.getName())) {
                            target = new File(dir, "camel-salesforce-component/target/classes");
                        } else if ("camel-linkedin".equals(dir.getName())) {
                            target = new File(dir, "camel-linkedin-component/target/classes");
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

        for (File file : jsonFiles) {
            File to = new File(componentsOutDir, file.getName());
            if (to.exists()) {
                duplicateJsonFiles.add(to);
                getLog().warn("Duplicate component name detected: " + to);
            }
            try {
                copyFile(file, to);
            } catch (IOException e) {
                throw new MojoFailureException("Cannot copy file from " + file + " -> " + to, e);
            }

            // check if we have a component label as we want the components to include labels
            try {
                String text = loadText(new FileInputStream(file));
                String name = asComponentName(file);
                Matcher matcher = LABEL_PATTERN.matcher(text);
                // grab the label, and remember it in the used labels
                if (matcher.find()) {
                    String label = matcher.group(1);
                    String[] labels = label.split(",");
                    for (String s : labels) {
                        Set<String> components = usedComponentLabels.get(s);
                        if (components == null) {
                            components = new TreeSet<String>();
                            usedComponentLabels.put(s, components);
                        }
                        components.add(name);
                    }
                }

                // check all the component options and grab the label(s) they use
                List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("componentProperties", text, true);
                for (Map<String, String> row : rows) {
                    String label = row.get("label");

                    if (label != null && !label.isEmpty()) {
                        String[] parts = label.split(",");
                        for (String part : parts) {
                            usedOptionLabels.add(part);
                        }
                    }
                }

                // check all the endpoint options and grab the label(s) they use
                int unused = 0;
                rows = JSonSchemaHelper.parseJsonSchema("properties", text, true);
                for (Map<String, String> row : rows) {
                    String label = row.get("label");

                    if (label != null && !label.isEmpty()) {
                        String[] parts = label.split(",");
                        for (String part : parts) {
                            usedOptionLabels.add(part);
                        }
                    } else {
                        unused++;
                    }
                }

                if (unused >= UNUSED_LABELS_WARN) {
                    unlabeledOptions.add(name);
                }

            } catch (IOException e) {
                // ignore
            }
        }

        File all = new File(componentsOutDir, "../components.properties");
        try {
            FileOutputStream fos = new FileOutputStream(all, false);

            String[] names = componentsOutDir.list();
            List<String> components = new ArrayList<String>();
            // sort the names
            for (String name : names) {
                if (name.endsWith(".json")) {
                    // strip out .json from the name
                    String componentName = name.substring(0, name.length() - 5);
                    components.add(componentName);
                }
            }

            Collections.sort(components);
            for (String name : components) {
                fos.write(name.getBytes());
                fos.write("\n".getBytes());
            }

            fos.close();

        } catch (IOException e) {
            throw new MojoFailureException("Error writing to file " + all);
        }

        printComponentsReport(jsonFiles, duplicateJsonFiles, missingComponents, usedComponentLabels, usedOptionLabels, unlabeledOptions);
    }

    protected void executeDataFormats() throws MojoExecutionException, MojoFailureException {
        getLog().info("Copying all Camel dataformat json descriptors");

        // lets use sorted set/maps
        Set<File> jsonFiles = new TreeSet<File>();
        Set<File> duplicateJsonFiles = new TreeSet<File>();
        Set<File> dataFormatFiles = new TreeSet<File>();
        Map<String, Set<String>> usedLabels = new TreeMap<String, Set<String>>();

        // find all data formats from the components directory
        if (componentsDir != null && componentsDir.isDirectory()) {
            File[] dataFormats = componentsDir.listFiles();
            if (dataFormats != null) {
                for (File dir : dataFormats) {
                    if (dir.isDirectory() && !"target".equals(dir.getName())) {
                        File target = new File(dir, "target/classes");
                        findDataFormatFilesRecursive(target, jsonFiles, dataFormatFiles, new CamelDataFormatsFileFilter());
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

        for (File file : jsonFiles) {
            File to = new File(dataFormatsOutDir, file.getName());
            if (to.exists()) {
                duplicateJsonFiles.add(to);
                getLog().warn("Duplicate dataformat name detected: " + to);
            }
            try {
                copyFile(file, to);
            } catch (IOException e) {
                throw new MojoFailureException("Cannot copy file from " + file + " -> " + to, e);
            }

            // check if we have a label as we want the data format to include labels
            try {
                String text = loadText(new FileInputStream(file));
                String name = asComponentName(file);
                Matcher matcher = LABEL_PATTERN.matcher(text);
                // grab the label, and remember it in the used labels
                if (matcher.find()) {
                    String label = matcher.group(1);
                    String[] labels = label.split(",");
                    for (String s : labels) {
                        Set<String> dataFormats = usedLabels.get(s);
                        if (dataFormats == null) {
                            dataFormats = new TreeSet<String>();
                            usedLabels.put(s, dataFormats);
                        }
                        dataFormats.add(name);
                    }
                }
            } catch (IOException e) {
                // ignore
            }
        }

        File all = new File(dataFormatsOutDir, "../dataformats.properties");
        try {
            FileOutputStream fos = new FileOutputStream(all, false);

            String[] names = dataFormatsOutDir.list();
            List<String> dataFormats = new ArrayList<String>();
            // sort the names
            for (String name : names) {
                if (name.endsWith(".json")) {
                    // strip out .json from the name
                    String dataFormatName = name.substring(0, name.length() - 5);
                    dataFormats.add(dataFormatName);
                }
            }

            Collections.sort(dataFormats);
            for (String name : dataFormats) {
                fos.write(name.getBytes());
                fos.write("\n".getBytes());
            }

            fos.close();

        } catch (IOException e) {
            throw new MojoFailureException("Error writing to file " + all);
        }

        printDataFormatsReport(jsonFiles, duplicateJsonFiles, usedLabels);
    }

    protected void executeLanguages() throws MojoExecutionException, MojoFailureException {
        getLog().info("Copying all Camel language json descriptors");

        // lets use sorted set/maps
        Set<File> jsonFiles = new TreeSet<File>();
        Set<File> duplicateJsonFiles = new TreeSet<File>();
        Set<File> languageFiles = new TreeSet<File>();
        Map<String, Set<String>> usedLabels = new TreeMap<String, Set<String>>();

        // find all languages from the components directory
        if (componentsDir != null && componentsDir.isDirectory()) {
            File[] languages = componentsDir.listFiles();
            if (languages != null) {
                for (File dir : languages) {
                    if (dir.isDirectory() && !"target".equals(dir.getName())) {
                        File target = new File(dir, "target/classes");
                        findLanguageFilesRecursive(target, jsonFiles, languageFiles, new CamelLanguagesFileFilter());
                    }
                }
            }
        }
        if (coreDir != null && coreDir.isDirectory()) {
            File target = new File(coreDir, "target/classes");
            findLanguageFilesRecursive(target, jsonFiles, languageFiles, new CamelLanguagesFileFilter());
        }

        getLog().info("Found " + languageFiles.size() + " language.properties files");
        getLog().info("Found " + jsonFiles.size() + " language json files");

        // make sure to create out dir
        languagesOutDir.mkdirs();

        for (File file : jsonFiles) {
            File to = new File(languagesOutDir, file.getName());
            if (to.exists()) {
                duplicateJsonFiles.add(to);
                getLog().warn("Duplicate language name detected: " + to);
            }
            try {
                copyFile(file, to);
            } catch (IOException e) {
                throw new MojoFailureException("Cannot copy file from " + file + " -> " + to, e);
            }

            // check if we have a label as we want the data format to include labels
            try {
                String text = loadText(new FileInputStream(file));
                String name = asComponentName(file);
                Matcher matcher = LABEL_PATTERN.matcher(text);
                // grab the label, and remember it in the used labels
                if (matcher.find()) {
                    String label = matcher.group(1);
                    String[] labels = label.split(",");
                    for (String s : labels) {
                        Set<String> languages = usedLabels.get(s);
                        if (languages == null) {
                            languages = new TreeSet<String>();
                            usedLabels.put(s, languages);
                        }
                        languages.add(name);
                    }
                }
            } catch (IOException e) {
                // ignore
            }
        }

        File all = new File(languagesOutDir, "../languages.properties");
        try {
            FileOutputStream fos = new FileOutputStream(all, false);

            String[] names = languagesOutDir.list();
            List<String> languages = new ArrayList<String>();
            // sort the names
            for (String name : names) {
                if (name.endsWith(".json")) {
                    // strip out .json from the name
                    String languageName = name.substring(0, name.length() - 5);
                    languages.add(languageName);
                }
            }

            Collections.sort(languages);
            for (String name : languages) {
                fos.write(name.getBytes());
                fos.write("\n".getBytes());
            }

            fos.close();

        } catch (IOException e) {
            throw new MojoFailureException("Error writing to file " + all);
        }

        printLanguagesReport(jsonFiles, duplicateJsonFiles, usedLabels);
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

    protected void executeDocuments() throws MojoExecutionException, MojoFailureException {
        getLog().info("Copying all Camel documents (ascii docs)");

        // lets use sorted set/maps
        Set<File> adocFiles = new TreeSet<File>();
        Set<File> missingAdocFiles = new TreeSet<File>();
        Set<File> duplicateAdocFiles = new TreeSet<File>();

        // find all languages from the components directory
        if (componentsDir != null && componentsDir.isDirectory()) {
            File[] components = componentsDir.listFiles();
            if (components != null) {
                for (File dir : components) {
                    if (dir.isDirectory() && !"target".equals(dir.getName()) && !dir.getName().startsWith(".")) {
                        File target = new File(dir, "src/main/docs");

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

        getLog().info("Found " + adocFiles.size() + " ascii document files");

        // make sure to create out dir
        documentsOutDir.mkdirs();

        for (File file : adocFiles) {
            File to = new File(documentsOutDir, file.getName());
            if (to.exists()) {
                duplicateAdocFiles.add(to);
                getLog().warn("Duplicate document name detected: " + to);
            }
            try {
                copyFile(file, to);
            } catch (IOException e) {
                throw new MojoFailureException("Cannot copy file from " + file + " -> " + to, e);
            }
        }

        File all = new File(documentsOutDir, "../docs.properties");
        try {
            FileOutputStream fos = new FileOutputStream(all, false);

            String[] names = documentsOutDir.list();
            List<String> documents = new ArrayList<String>();
            // sort the names
            for (String name : names) {
                if (name.endsWith(".adoc")) {
                    // strip out .json from the name
                    String documentName = name.substring(0, name.length() - 5);
                    documents.add(documentName);
                }
            }

            Collections.sort(documents);
            for (String name : documents) {
                fos.write(name.getBytes());
                fos.write("\n".getBytes());
            }

            fos.close();

        } catch (IOException e) {
            throw new MojoFailureException("Error writing to file " + all);
        }

        printDocumentsReport(adocFiles, duplicateAdocFiles, missingAdocFiles);
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

    private void printComponentsReport(Set<File> json, Set<File> duplicate, Set<File> missing, Map<String,
            Set<String>> usedComponentLabels, Set<String> usedOptionsLabels, Set<String> unusedLabels) {
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
        getLog().info("");
        getLog().info("================================================================================");
    }

    private void printDataFormatsReport(Set<File> json, Set<File> duplicate, Map<String, Set<String>> usedLabels) {
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
        getLog().info("");
        getLog().info("================================================================================");
    }

    private void printLanguagesReport(Set<File> json, Set<File> duplicate, Map<String, Set<String>> usedLabels) {
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
        if (name.endsWith(".json") || name.endsWith(".adoc")) {
            return name.substring(0, name.length() - 5);
        }
        return name;
    }

    private void findComponentFilesRecursive(File dir, Set<File> found, Set<File> components, FileFilter filter) {
        File[] files = dir.listFiles(filter);
        if (files != null) {
            for (File file : files) {
                // skip files in root dirs as Camel does not store information there but others may do
                boolean rootDir = "classes".equals(dir.getName()) || "META-INF".equals(dir.getName());
                boolean jsonFile = !rootDir && file.isFile() && file.getName().endsWith(".json");
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
                // skip files in root dirs as Camel does not store information there but others may do
                boolean rootDir = "classes".equals(dir.getName()) || "META-INF".equals(dir.getName());
                boolean jsonFile = !rootDir && file.isFile() && file.getName().endsWith(".json");
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
                // skip files in root dirs as Camel does not store information there but others may do
                boolean rootDir = "classes".equals(dir.getName()) || "META-INF".equals(dir.getName());
                boolean jsonFile = !rootDir && file.isFile() && file.getName().endsWith(".json");
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

    private void findAsciiDocFilesRecursive(File dir, Set<File> found, FileFilter filter) {
        File[] files = dir.listFiles(filter);
        if (files != null) {
            for (File file : files) {
                // skip files in root dirs as Camel does not store information there but others may do
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
                // do not check the camel-core model packages as there is no components there
                return false;
            }
            if (pathname.isFile() && pathname.getName().endsWith(".json")) {
                // must be a components json file
                try {
                    String json = loadText(new FileInputStream(pathname));
                    return json != null && json.contains("\"kind\": \"component\"");
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
                // do not check the camel-core model packages as there is no components there
                return false;
            }
            if (pathname.isFile() && pathname.getName().endsWith(".json")) {
                // must be a dataformat json file
                try {
                    String json = loadText(new FileInputStream(pathname));
                    return json != null && json.contains("\"kind\": \"dataformat\"");
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
                // do not check the camel-core model packages as there is no components there
                return false;
            }
            if (pathname.isFile() && pathname.getName().endsWith(".json")) {
                // must be a language json file
                try {
                    String json = loadText(new FileInputStream(pathname));
                    return json != null && json.contains("\"kind\": \"language\"");
                } catch (IOException e) {
                    // ignore
                }
            }
            return pathname.isDirectory() || (pathname.isFile() && pathname.getName().equals("language.properties"));
        }
    }

    private class CamelAsciiDocFileFilter implements FileFilter {

        @Override
        public boolean accept(File pathname) {
            return pathname.isFile() && pathname.getName().endsWith(".adoc");
        }
    }

    public static void copyFile(File from, File to) throws IOException {
        FileChannel in = null;
        FileChannel out = null;
        try {
            in = new FileInputStream(from).getChannel();
            out = new FileOutputStream(to).getChannel();

            long size = in.size();
            long position = 0;
            while (position < size) {
                position += in.transferTo(position, BUFFER_SIZE, out);
            }
        } finally {
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
        }
    }

}