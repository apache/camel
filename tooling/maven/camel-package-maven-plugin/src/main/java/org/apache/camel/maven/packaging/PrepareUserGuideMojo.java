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
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import edu.emory.mathcs.backport.java.util.Collections;
import org.apache.camel.maven.packaging.model.ComponentModel;
import org.apache.camel.maven.packaging.model.DataFormatModel;
import org.apache.camel.maven.packaging.model.LanguageModel;
import org.apache.camel.maven.packaging.model.OtherModel;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

import static org.apache.camel.maven.packaging.PackageHelper.loadText;
import static org.apache.camel.maven.packaging.PackageHelper.writeText;

/**
 * Prepares the user guide to keep the table of content up to date with the components, data formats, and languages.
 *
 * @goal prepare-user-guide
 */
public class PrepareUserGuideMojo extends AbstractMojo {

    /**
     * The maven project.
     *
     * @parameter property="project"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /**
     * The directory for components catalog
     *
     * @parameter default-value="${project.build.directory}/classes/org/apache/camel/catalog/components"
     */
    protected File componentsDir;

    /**
     * The directory for data formats catalog
     *
     * @parameter default-value="${project.build.directory}/classes/org/apache/camel/catalog/dataformats"
     */
    protected File dataFormatsDir;

    /**
     * The directory for languages catalog
     *
     * @parameter default-value="${project.build.directory}/classes/org/apache/camel/catalog/languages"
     */
    protected File languagesDir;

    /**
     * The directory for others catalog
     *
     * @parameter default-value="${project.build.directory}/classes/org/apache/camel/catalog/others"
     */
    protected File othersDir;

    /**
     * The directory for the user guide
     *
     * @parameter default-value="${project.directory}/../../../docs/user-manual/en"
     */
    protected File userGuideDir;

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
     * @throws MojoExecutionException execution of the main class or one of the
     *                                                        threads it generated failed.
     * @throws MojoFailureException   something bad happened...
     */
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
                String json = loadText(new FileInputStream(file));
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
                String json = loadText(new FileInputStream(file));
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
                String json = loadText(new FileInputStream(file));
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
                String json = loadText(new FileInputStream(file));
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
            String text = loadText(new FileInputStream(file));

            String existing = StringHelper.between(text, "<!-- core components: START -->", "<!-- core components: END -->");
            if (existing != null) {
                // remove leading line breaks etc
                existing = existing.trim();
                changed = changed.trim();
                if (existing.equals(changed)) {
                    return false;
                } else {
                    String before = StringHelper.before(text, "<!-- core components: START -->");
                    String after = StringHelper.after(text, "<!-- core components: END -->");
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
            String text = loadText(new FileInputStream(file));

            String existing = StringHelper.between(text, "<!-- components: START -->", "<!-- components: END -->");
            if (existing != null) {
                // remove leading line breaks etc
                existing = existing.trim();
                changed = changed.trim();
                if (existing.equals(changed)) {
                    return false;
                } else {
                    String before = StringHelper.before(text, "<!-- components: START -->");
                    String after = StringHelper.after(text, "<!-- components: END -->");
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
            String text = loadText(new FileInputStream(file));

            String existing = StringHelper.between(text, "<!-- others: START -->", "<!-- others: END -->");
            if (existing != null) {
                // remove leading line breaks etc
                existing = existing.trim();
                changed = changed.trim();
                if (existing.equals(changed)) {
                    return false;
                } else {
                    String before = StringHelper.before(text, "<!-- others: START -->");
                    String after = StringHelper.after(text, "<!-- others: END -->");
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
            String text = loadText(new FileInputStream(file));

            String existing = StringHelper.between(text, "<!-- dataformats: START -->", "<!-- dataformats: END -->");
            if (existing != null) {
                // remove leading line breaks etc
                existing = existing.trim();
                changed = changed.trim();
                if (existing.equals(changed)) {
                    return false;
                } else {
                    String before = StringHelper.before(text, "<!-- dataformats: START -->");
                    String after = StringHelper.after(text, "<!-- dataformats: END -->");
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
            String text = loadText(new FileInputStream(file));

            String existing = StringHelper.between(text, "<!-- languages: START -->", "<!-- languages: END -->");
            if (existing != null) {
                // remove leading line breaks etc
                existing = existing.trim();
                changed = changed.trim();
                if (existing.equals(changed)) {
                    return false;
                } else {
                    String before = StringHelper.before(text, "<!-- languages: START -->");
                    String after = StringHelper.after(text, "<!-- languages: END -->");
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
        List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("component", json, false);

        ComponentModel component = new ComponentModel(true);
        component.setScheme(JSonSchemaHelper.getSafeValue("scheme", rows));
        component.setSyntax(JSonSchemaHelper.getSafeValue("syntax", rows));
        component.setAlternativeSyntax(JSonSchemaHelper.getSafeValue("alternativeSyntax", rows));
        component.setAlternativeSchemes(JSonSchemaHelper.getSafeValue("alternativeSchemes", rows));
        component.setTitle(JSonSchemaHelper.getSafeValue("title", rows));
        component.setDescription(JSonSchemaHelper.getSafeValue("description", rows));
        component.setFirstVersion(JSonSchemaHelper.getSafeValue("firstVersion", rows));
        component.setLabel(JSonSchemaHelper.getSafeValue("label", rows));
        component.setDeprecated(JSonSchemaHelper.getSafeValue("deprecated", rows));
        component.setDeprecationNote(JSonSchemaHelper.getSafeValue("deprecationNote", rows));
        component.setConsumerOnly(JSonSchemaHelper.getSafeValue("consumerOnly", rows));
        component.setProducerOnly(JSonSchemaHelper.getSafeValue("producerOnly", rows));
        component.setJavaType(JSonSchemaHelper.getSafeValue("javaType", rows));
        component.setGroupId(JSonSchemaHelper.getSafeValue("groupId", rows));
        component.setArtifactId(JSonSchemaHelper.getSafeValue("artifactId", rows));
        component.setVersion(JSonSchemaHelper.getSafeValue("version", rows));

        return component;
    }

    private OtherModel generateOtherModel(String json) {
        List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("other", json, false);

        OtherModel other = new OtherModel();
        other.setName(JSonSchemaHelper.getSafeValue("name", rows));
        other.setTitle(JSonSchemaHelper.getSafeValue("title", rows));
        other.setDescription(JSonSchemaHelper.getSafeValue("description", rows));
        other.setFirstVersion(JSonSchemaHelper.getSafeValue("firstVersion", rows));
        other.setLabel(JSonSchemaHelper.getSafeValue("label", rows));
        other.setDeprecated(JSonSchemaHelper.getSafeValue("deprecated", rows));
        other.setDeprecationNote(JSonSchemaHelper.getSafeValue("deprecationNote", rows));
        other.setGroupId(JSonSchemaHelper.getSafeValue("groupId", rows));
        other.setArtifactId(JSonSchemaHelper.getSafeValue("artifactId", rows));
        other.setVersion(JSonSchemaHelper.getSafeValue("version", rows));

        return other;
    }

    private DataFormatModel generateDataFormatModel(String json) {
        List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("dataformat", json, false);

        DataFormatModel dataFormat = new DataFormatModel();
        dataFormat.setName(JSonSchemaHelper.getSafeValue("name", rows));
        dataFormat.setTitle(JSonSchemaHelper.getSafeValue("title", rows));
        dataFormat.setModelName(JSonSchemaHelper.getSafeValue("modelName", rows));
        dataFormat.setDescription(JSonSchemaHelper.getSafeValue("description", rows));
        dataFormat.setFirstVersion(JSonSchemaHelper.getSafeValue("firstVersion", rows));
        dataFormat.setLabel(JSonSchemaHelper.getSafeValue("label", rows));
        dataFormat.setDeprecated(JSonSchemaHelper.getSafeValue("deprecated", rows));
        dataFormat.setDeprecationNote(JSonSchemaHelper.getSafeValue("deprecationNote", rows));
        dataFormat.setJavaType(JSonSchemaHelper.getSafeValue("javaType", rows));
        dataFormat.setGroupId(JSonSchemaHelper.getSafeValue("groupId", rows));
        dataFormat.setArtifactId(JSonSchemaHelper.getSafeValue("artifactId", rows));
        dataFormat.setVersion(JSonSchemaHelper.getSafeValue("version", rows));

        return dataFormat;
    }

    private LanguageModel generateLanguageModel(String json) {
        List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("language", json, false);

        LanguageModel language = new LanguageModel();
        language.setTitle(JSonSchemaHelper.getSafeValue("title", rows));
        language.setName(JSonSchemaHelper.getSafeValue("name", rows));
        language.setModelName(JSonSchemaHelper.getSafeValue("modelName", rows));
        language.setDescription(JSonSchemaHelper.getSafeValue("description", rows));
        language.setFirstVersion(JSonSchemaHelper.getSafeValue("firstVersion", rows));
        language.setLabel(JSonSchemaHelper.getSafeValue("label", rows));
        language.setDeprecated(JSonSchemaHelper.getSafeValue("deprecated", rows));
        language.setDeprecationNote(JSonSchemaHelper.getSafeValue("deprecationNote", rows));
        language.setJavaType(JSonSchemaHelper.getSafeValue("javaType", rows));
        language.setGroupId(JSonSchemaHelper.getSafeValue("groupId", rows));
        language.setArtifactId(JSonSchemaHelper.getSafeValue("artifactId", rows));
        language.setVersion(JSonSchemaHelper.getSafeValue("version", rows));

        return language;
    }

}
