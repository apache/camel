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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.camel.maven.packaging.model.ComponentModel;
import org.apache.camel.maven.packaging.model.DataFormatModel;
import org.apache.camel.maven.packaging.model.LanguageModel;
import org.apache.camel.maven.packaging.model.OtherModel;
import org.apache.camel.tooling.util.JSonSchemaHelper;
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
import static org.apache.camel.tooling.util.PackageHelper.writeText;
import static org.apache.camel.tooling.util.JSonSchemaHelper.getSafeValue;
import static org.apache.camel.tooling.util.JSonSchemaHelper.parseJsonSchema;

/**
 * Prepares the user guide to keep the table of content up to date with the
 * components, data formats, and languages.
 */
@Mojo(name = "prepare-user-guide", threadSafe = true)
public class PrepareUserGuideMojo extends AbstractMojo {

    /**
     * The maven project.
     */
    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;

    /**
     * The directory for components catalog
     */
    @Parameter(defaultValue = "${project.build.directory}/classes/org/apache/camel/catalog/components")
    protected File componentsDir;

    /**
     * The directory for data formats catalog
     */
    @Parameter(defaultValue = "${project.build.directory}/classes/org/apache/camel/catalog/dataformats")
    protected File dataFormatsDir;

    /**
     * The directory for languages catalog
     */
    @Parameter(defaultValue = "${project.build.directory}/classes/org/apache/camel/catalog/languages")
    protected File languagesDir;

    /**
     * The directory for others catalog
     */
    @Parameter(defaultValue = "${project.build.directory}/classes/org/apache/camel/catalog/others")
    protected File othersDir;

    /**
     * The directory for the user guide
     */
    @Parameter(defaultValue = "${project.directory}/../../../docs/user-manual/en")
    protected File userGuideDir;

    /**
     * Maven ProjectHelper.
     */
    @Component
    private MavenProjectHelper projectHelper;

    /**
     * Execute goal.
     *
     * @throws MojoExecutionException execution of the main class or one of the
     *             threads it generated failed.
     * @throws MojoFailureException something bad happened...
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        executeComponents();
        executeOthers();
        executeDataFormats();
        executeLanguages();
    }

    protected void executeComponents() throws MojoExecutionException, MojoFailureException {
        Set<File> componentFiles = new TreeSet<>();

        if (componentsDir != null && componentsDir.isDirectory()) {
            File[] files = componentsDir.listFiles();
            if (files != null) {
                componentFiles.addAll(Arrays.asList(files));
            }
        }

        try {
            List<ComponentModel> models = new ArrayList<>();
            for (File file : componentFiles) {
                String json = loadText(file);
                ComponentModel model = generateComponentModel(json);

                // filter out alternative schemas which reuses documentation
                boolean add = true;
                if (!model.getAlternativeSchemes().isEmpty()) {
                    String first = model.getAlternativeSchemes().split(",")[0];
                    if (!model.getScheme().equals(first)) {
                        add = false;
                    }
                }
                if (add) {
                    models.add(model);
                }
            }

            // sor the models
            Collections.sort(models, new ComponentComparator());

            // the summary file has the TOC
            File file = new File(userGuideDir, "SUMMARY.md");

            // update core components
            StringBuilder core = new StringBuilder();
            core.append("* Core Components\n");
            for (ComponentModel model : models) {
                if (model.getLabel().contains("core")) {
                    String line = "\t* " + link(model) + "\n";
                    core.append(line);
                }
            }
            boolean updated = updateCoreComponents(file, core.toString());

            // update regular components
            StringBuilder regular = new StringBuilder();
            regular.append("* Components\n");
            for (ComponentModel model : models) {
                if (!model.getLabel().contains("core")) {
                    String line = "\t* " + link(model) + "\n";
                    regular.append(line);
                }
            }
            updated |= updateComponents(file, regular.toString());

            if (updated) {
                getLog().info("Updated user guide file: " + file);
            } else {
                getLog().debug("No changes to user guide file: " + file);
            }

        } catch (IOException e) {
            throw new MojoFailureException("Error due " + e.getMessage(), e);
        }
    }

    protected void executeOthers() throws MojoExecutionException, MojoFailureException {
        Set<File> otherFiles = new TreeSet<>();

        if (othersDir != null && othersDir.isDirectory()) {
            File[] files = othersDir.listFiles();
            if (files != null) {
                otherFiles.addAll(Arrays.asList(files));
            }
        }

        try {
            List<OtherModel> models = new ArrayList<>();
            for (File file : otherFiles) {
                String json = loadText(file);
                OtherModel model = generateOtherModel(json);
                models.add(model);
            }

            // sor the models
            Collections.sort(models, new OtherComparator());

            // the summary file has the TOC
            File file = new File(userGuideDir, "SUMMARY.md");

            // update core components
            StringBuilder other = new StringBuilder();
            other.append("* Miscellaneous Components\n");
            for (OtherModel model : models) {
                String line = "\t* " + link(model) + "\n";
                other.append(line);
            }
            boolean updated = updateOthers(file, other.toString());

            if (updated) {
                getLog().info("Updated user guide file: " + file);
            } else {
                getLog().debug("No changes to user guide file: " + file);
            }

        } catch (IOException e) {
            throw new MojoFailureException("Error due " + e.getMessage(), e);
        }
    }

    protected void executeDataFormats() throws MojoExecutionException, MojoFailureException {
        Set<File> dataFormatFiles = new TreeSet<>();

        if (dataFormatsDir != null && dataFormatsDir.isDirectory()) {
            File[] files = dataFormatsDir.listFiles();
            if (files != null) {
                dataFormatFiles.addAll(Arrays.asList(files));
            }
        }

        try {
            List<DataFormatModel> models = new ArrayList<>();
            for (File file : dataFormatFiles) {
                String json = loadText(file);
                DataFormatModel model = generateDataFormatModel(json);
                models.add(model);
            }

            // sor the models
            Collections.sort(models, new DataFormatComparator());

            // the summary file has the TOC
            File file = new File(userGuideDir, "SUMMARY.md");

            // update data formats
            StringBuilder dataFormats = new StringBuilder();
            dataFormats.append("* Data Formats\n");
            for (DataFormatModel model : models) {
                String line = "\t* " + link(model) + "\n";
                dataFormats.append(line);
            }
            boolean updated = updateDataFormats(file, dataFormats.toString());

            if (updated) {
                getLog().info("Updated user guide file: " + file);
            } else {
                getLog().debug("No changes to user guide file: " + file);
            }

        } catch (IOException e) {
            throw new MojoFailureException("Error due " + e.getMessage(), e);
        }
    }

    protected void executeLanguages() throws MojoExecutionException, MojoFailureException {
        Set<File> languageFiles = new TreeSet<>();

        if (languagesDir != null && languagesDir.isDirectory()) {
            File[] files = languagesDir.listFiles();
            if (files != null) {
                languageFiles.addAll(Arrays.asList(files));
            }
        }

        try {
            List<LanguageModel> models = new ArrayList<>();
            for (File file : languageFiles) {
                String json = loadText(file);
                LanguageModel model = generateLanguageModel(json);
                models.add(model);
            }

            // sor the models
            Collections.sort(models, new LanguageComparator());

            // the summary file has the TOC
            File file = new File(userGuideDir, "SUMMARY.md");

            // update languages
            StringBuilder languages = new StringBuilder();
            languages.append("* Expression Languages\n");
            for (LanguageModel model : models) {
                String line = "\t* " + link(model) + "\n";
                languages.append(line);
            }
            boolean updated = updateLanguages(file, languages.toString());

            if (updated) {
                getLog().info("Updated user guide file: " + file);
            } else {
                getLog().debug("No changes to user guide file: " + file);
            }

        } catch (IOException e) {
            throw new MojoFailureException("Error due " + e.getMessage(), e);
        }
    }

    private boolean updateCoreComponents(File file, String changed) throws MojoExecutionException {
        if (!file.exists()) {
            return false;
        }

        try {
            String text = loadText(file);

            String existing = Strings.between(text, "<!-- core components: START -->", "<!-- core components: END -->");
            if (existing != null) {
                // remove leading line breaks etc
                existing = existing.trim();
                changed = changed.trim();
                if (existing.equals(changed)) {
                    return false;
                } else {
                    String before = Strings.before(text, "<!-- core components: START -->");
                    String after = Strings.after(text, "<!-- core components: END -->");
                    text = before + "<!-- core components: START -->\n" + changed + "\n<!-- core components: END -->" + after;
                    writeText(file, text);
                    return true;
                }
            } else {
                getLog().warn("Cannot find markers in file " + file);
                getLog().warn("Add the following markers");
                getLog().warn("\t<!-- core components: START -->");
                getLog().warn("\t<!-- core components: END -->");
                return false;
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error reading file " + file + " Reason: " + e, e);
        }
    }

    private boolean updateComponents(File file, String changed) throws MojoExecutionException {
        if (!file.exists()) {
            return false;
        }

        try {
            String text = loadText(file);

            String existing = Strings.between(text, "<!-- components: START -->", "<!-- components: END -->");
            if (existing != null) {
                // remove leading line breaks etc
                existing = existing.trim();
                changed = changed.trim();
                if (existing.equals(changed)) {
                    return false;
                } else {
                    String before = Strings.before(text, "<!-- components: START -->");
                    String after = Strings.after(text, "<!-- components: END -->");
                    text = before + "<!-- components: START -->\n" + changed + "\n<!-- components: END -->" + after;
                    writeText(file, text);
                    return true;
                }
            } else {
                getLog().warn("Cannot find markers in file " + file);
                getLog().warn("Add the following markers");
                getLog().warn("\t<!-- components: START -->");
                getLog().warn("\t<!-- components: END -->");
                return false;
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error reading file " + file + " Reason: " + e, e);
        }
    }

    private boolean updateOthers(File file, String changed) throws MojoExecutionException {
        if (!file.exists()) {
            return false;
        }

        try {
            String text = loadText(file);

            String existing = Strings.between(text, "<!-- others: START -->", "<!-- others: END -->");
            if (existing != null) {
                // remove leading line breaks etc
                existing = existing.trim();
                changed = changed.trim();
                if (existing.equals(changed)) {
                    return false;
                } else {
                    String before = Strings.before(text, "<!-- others: START -->");
                    String after = Strings.after(text, "<!-- others: END -->");
                    text = before + "<!-- others: START -->\n" + changed + "\n<!-- others: END -->" + after;
                    writeText(file, text);
                    return true;
                }
            } else {
                getLog().warn("Cannot find markers in file " + file);
                getLog().warn("Add the following markers");
                getLog().warn("\t<!-- others: START -->");
                getLog().warn("\t<!-- others: END -->");
                return false;
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error reading file " + file + " Reason: " + e, e);
        }
    }

    private boolean updateDataFormats(File file, String changed) throws MojoExecutionException {
        if (!file.exists()) {
            return false;
        }

        try {
            String text = loadText(file);

            String existing = Strings.between(text, "<!-- dataformats: START -->", "<!-- dataformats: END -->");
            if (existing != null) {
                // remove leading line breaks etc
                existing = existing.trim();
                changed = changed.trim();
                if (existing.equals(changed)) {
                    return false;
                } else {
                    String before = Strings.before(text, "<!-- dataformats: START -->");
                    String after = Strings.after(text, "<!-- dataformats: END -->");
                    text = before + "<!-- dataformats: START -->\n" + changed + "\n<!-- dataformats: END -->" + after;
                    writeText(file, text);
                    return true;
                }
            } else {
                getLog().warn("Cannot find markers in file " + file);
                getLog().warn("Add the following markers");
                getLog().warn("\t<!-- dataformats: START -->");
                getLog().warn("\t<!-- dataformats: END -->");
                return false;
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error reading file " + file + " Reason: " + e, e);
        }
    }

    private boolean updateLanguages(File file, String changed) throws MojoExecutionException {
        if (!file.exists()) {
            return false;
        }

        try {
            String text = loadText(file);

            String existing = Strings.between(text, "<!-- languages: START -->", "<!-- languages: END -->");
            if (existing != null) {
                // remove leading line breaks etc
                existing = existing.trim();
                changed = changed.trim();
                if (existing.equals(changed)) {
                    return false;
                } else {
                    String before = Strings.before(text, "<!-- languages: START -->");
                    String after = Strings.after(text, "<!-- languages: END -->");
                    text = before + "<!-- languages: START -->\n" + changed + "\n<!-- languages: END -->" + after;
                    writeText(file, text);
                    return true;
                }
            } else {
                getLog().warn("Cannot find markers in file " + file);
                getLog().warn("Add the following markers");
                getLog().warn("\t<!-- languages: START -->");
                getLog().warn("\t<!-- languages: END -->");
                return false;
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error reading file " + file + " Reason: " + e, e);
        }
    }

    private static String link(ComponentModel model) {
        return "[" + model.getTitle() + "](" + model.getScheme() + "-component.adoc)";
    }

    private static String link(OtherModel model) {
        return "[" + model.getTitle() + "](" + model.getName() + ".adoc)";
    }

    private static String link(DataFormatModel model) {
        // special for some data formats
        String name = asDataFormatName(model.getName());
        return "[" + model.getTitle() + "](" + name + "-dataformat.adoc)";
    }

    private static String link(LanguageModel model) {
        return "[" + model.getTitle() + "](" + model.getName() + "-language.adoc)";
    }

    private static String asDataFormatName(String name) {
        // special for some dataformats which share the same readme file
        if (name.startsWith("bindy")) {
            return "bindy";
        } else {
            return name;
        }
    }

    private static class ComponentComparator implements Comparator<ComponentModel> {

        @Override
        public int compare(ComponentModel o1, ComponentModel o2) {
            // lets sort by title
            return o1.getTitle().compareToIgnoreCase(o2.getTitle());
        }
    }

    private static class OtherComparator implements Comparator<OtherModel> {

        @Override
        public int compare(OtherModel o1, OtherModel o2) {
            // lets sort by title
            return o1.getTitle().compareToIgnoreCase(o2.getTitle());
        }
    }

    private static class DataFormatComparator implements Comparator<DataFormatModel> {

        @Override
        public int compare(DataFormatModel o1, DataFormatModel o2) {
            // lets sort by title
            return o1.getTitle().compareToIgnoreCase(o2.getTitle());
        }
    }

    private static class LanguageComparator implements Comparator<LanguageModel> {

        @Override
        public int compare(LanguageModel o1, LanguageModel o2) {
            // lets sort by title
            return o1.getTitle().compareToIgnoreCase(o2.getTitle());
        }
    }

    private ComponentModel generateComponentModel(String json) {
        List<Map<String, String>> rows = parseJsonSchema("component", json, false);

        ComponentModel component = new ComponentModel();
        component.setScheme(getSafeValue("scheme", rows));
        component.setSyntax(getSafeValue("syntax", rows));
        component.setAlternativeSyntax(getSafeValue("alternativeSyntax", rows));
        component.setAlternativeSchemes(getSafeValue("alternativeSchemes", rows));
        component.setTitle(getSafeValue("title", rows));
        component.setDescription(getSafeValue("description", rows));
        component.setFirstVersion(getSafeValue("firstVersion", rows));
        component.setLabel(getSafeValue("label", rows));
        component.setDeprecated(getSafeValue("deprecated", rows));
        component.setDeprecationNote(getSafeValue("deprecationNote", rows));
        component.setConsumerOnly(getSafeValue("consumerOnly", rows));
        component.setProducerOnly(getSafeValue("producerOnly", rows));
        component.setJavaType(getSafeValue("javaType", rows));
        component.setGroupId(getSafeValue("groupId", rows));
        component.setArtifactId(getSafeValue("artifactId", rows));
        component.setVersion(getSafeValue("version", rows));

        return component;
    }

    private OtherModel generateOtherModel(String json) {
        List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("other", json, false);

        OtherModel other = new OtherModel();
        other.setName(getSafeValue("name", rows));
        other.setTitle(getSafeValue("title", rows));
        other.setDescription(getSafeValue("description", rows));
        other.setFirstVersion(getSafeValue("firstVersion", rows));
        other.setLabel(getSafeValue("label", rows));
        other.setDeprecated(getSafeValue("deprecated", rows));
        other.setDeprecationNote(getSafeValue("deprecationNote", rows));
        other.setGroupId(getSafeValue("groupId", rows));
        other.setArtifactId(getSafeValue("artifactId", rows));
        other.setVersion(getSafeValue("version", rows));

        return other;
    }

    private DataFormatModel generateDataFormatModel(String json) {
        List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("dataformat", json, false);

        DataFormatModel dataFormat = new DataFormatModel();
        dataFormat.setName(getSafeValue("name", rows));
        dataFormat.setTitle(getSafeValue("title", rows));
        dataFormat.setModelName(getSafeValue("modelName", rows));
        dataFormat.setDescription(getSafeValue("description", rows));
        dataFormat.setFirstVersion(getSafeValue("firstVersion", rows));
        dataFormat.setLabel(getSafeValue("label", rows));
        dataFormat.setDeprecated(getSafeValue("deprecated", rows));
        dataFormat.setDeprecationNote(getSafeValue("deprecationNote", rows));
        dataFormat.setJavaType(getSafeValue("javaType", rows));
        dataFormat.setGroupId(getSafeValue("groupId", rows));
        dataFormat.setArtifactId(getSafeValue("artifactId", rows));
        dataFormat.setVersion(getSafeValue("version", rows));

        return dataFormat;
    }

    private LanguageModel generateLanguageModel(String json) {
        List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("language", json, false);

        LanguageModel language = new LanguageModel();
        language.setTitle(getSafeValue("title", rows));
        language.setName(getSafeValue("name", rows));
        language.setModelName(getSafeValue("modelName", rows));
        language.setDescription(getSafeValue("description", rows));
        language.setFirstVersion(getSafeValue("firstVersion", rows));
        language.setLabel(getSafeValue("label", rows));
        language.setDeprecated(getSafeValue("deprecated", rows));
        language.setDeprecationNote(getSafeValue("deprecationNote", rows));
        language.setJavaType(getSafeValue("javaType", rows));
        language.setGroupId(getSafeValue("groupId", rows));
        language.setArtifactId(getSafeValue("artifactId", rows));
        language.setVersion(getSafeValue("version", rows));

        return language;
    }

}
