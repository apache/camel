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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import edu.emory.mathcs.backport.java.util.Collections;
import org.apache.camel.maven.packaging.model.ComponentModel;
import org.apache.camel.maven.packaging.model.DataFormatModel;
import org.apache.camel.maven.packaging.model.EipModel;
import org.apache.camel.maven.packaging.model.LanguageModel;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.mvel2.templates.TemplateRuntime;

import static org.apache.camel.maven.packaging.PackageHelper.loadText;
import static org.apache.camel.maven.packaging.PackageHelper.writeText;

/**
 * Prepares the readme.md files content up to date with the components, data formats, and languages.
 *
 * @goal prepare-readme
 */
public class PrepareReadmeMojo extends AbstractMojo {

    /**
     * The maven project.
     *
     * @parameter property="project"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /**
     * The directory for EIPs (model) catalog
     *
     * @parameter default-value="${project.build.directory}/classes/org/apache/camel/catalog/models"
     */
    protected File eipsDir;

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
     * The directory for camel-core
     *
     * @parameter default-value="${project.directory}/../../../camel-core"
     */
    protected File readmeCoreDir;

    /**
     * The directory for components
     *
     * @parameter default-value="${project.directory}/../../../components"
     */
    protected File readmeComponentsDir;

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
        // update readme file in camel-core
        executeEipsReadme();
        executeComponentsReadme(true);
        executeDataFormatsReadme(true);
        executeLanguagesReadme(true);
        // update readme file in components
        executeComponentsReadme(false);
        executeDataFormatsReadme(false);
        executeLanguagesReadme(false);
    }

    protected void executeEipsReadme() throws MojoExecutionException, MojoFailureException {
        Set<File> eipFiles = new TreeSet<>();

        if (eipsDir != null && eipsDir.isDirectory()) {
            File[] files = eipsDir.listFiles();
            if (files != null) {
                eipFiles.addAll(Arrays.asList(files));
            }
        }

        try {
            List<EipModel> models = new ArrayList<>();
            for (File file : eipFiles) {
                String json = loadText(new FileInputStream(file));
                EipModel model = generateEipModel(json);

                // we only want actual EIPs from the models
                if (model.getLabel().startsWith("eip")) {
                    models.add(model);
                }
            }

            // re-order the EIPs so we have them in different categories

            // sort the models
            Collections.sort(models, new EipComparator());

            // update the big readme file in the core dir
            File file = new File(readmeCoreDir, "readme-eip.adoc");

            // update regular components
            boolean exists = file.exists();
            String changed = templateEips(models);
            boolean updated = updateEips(file, changed);

            if (updated) {
                getLog().info("Updated readme-eip.adoc file: " + file);
            } else if (exists) {
                getLog().debug("No changes to readme-eip.adoc file: " + file);
            } else {
                getLog().warn("No readme-eip.adoc file: " + file);
            }

        } catch (IOException e) {
            throw new MojoFailureException("Error due " + e.getMessage(), e);
        }
    }

    protected void executeComponentsReadme(boolean core) throws MojoExecutionException, MojoFailureException {
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

            // sort the models
            Collections.sort(models, new ComponentComparator());

            // filter out unwanted components
            List<ComponentModel> components = new ArrayList<>();
            for (ComponentModel model : models) {
                if (core && "camel-core".equals(model.getArtifactId())) {
                    components.add(model);
                } else if (!core && !"camel-core".equals(model.getArtifactId())) {
                    components.add(model);
                }
            }

            // update the big readme file in the core/components dir
            File file;
            if (core) {
                file = new File(readmeCoreDir, "readme.adoc");
            } else {
                file = new File(readmeComponentsDir, "readme.adoc");
            }

            // update regular components
            boolean exists = file.exists();
            String changed = templateComponents(components);
            boolean updated = updateComponents(file, changed);

            if (updated) {
                getLog().info("Updated readme.adoc file: " + file);
            } else if (exists) {
                getLog().debug("No changes to readme.adoc file: " + file);
            } else {
                getLog().warn("No readme.adoc file: " + file);
            }

        } catch (IOException e) {
            throw new MojoFailureException("Error due " + e.getMessage(), e);
        }
    }

    protected void executeDataFormatsReadme(boolean core) throws MojoExecutionException, MojoFailureException {
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

                // special for bindy as we have one common file
                if (model.getName().startsWith("bindy")) {
                    model.setName("bindy");
                }

                models.add(model);
            }

            // sort the models
            Collections.sort(models, new DataFormatComparator());

            // filter out camel-core
            List<DataFormatModel> dataFormats = new ArrayList<>();
            for (DataFormatModel model : models) {
                if (core && "camel-core".equals(model.getArtifactId())) {
                    dataFormats.add(model);
                } else if (!core && !"camel-core".equals(model.getArtifactId())) {
                    dataFormats.add(model);
                }
            }

            // update the big readme file in the core/components dir
            File file;
            if (core) {
                file = new File(readmeCoreDir, "readme.adoc");
            } else {
                file = new File(readmeComponentsDir, "readme.adoc");
            }

            // update regular data formats
            boolean exists = file.exists();
            String changed = templateDataFormats(dataFormats);
            boolean updated = updateDataFormats(file, changed);

            if (updated) {
                getLog().info("Updated readme.adoc file: " + file);
            } else if (exists) {
                getLog().debug("No changes to readme.adoc file: " + file);
            } else {
                getLog().warn("No readme.adoc file: " + file);
            }

        } catch (IOException e) {
            throw new MojoFailureException("Error due " + e.getMessage(), e);
        }
    }

    protected void executeLanguagesReadme(boolean core) throws MojoExecutionException, MojoFailureException {
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

            // sort the models
            Collections.sort(models, new LanguageComparator());

            // filter out camel-core
            List<LanguageModel> languages = new ArrayList<>();
            for (LanguageModel model : models) {
                if (core && "camel-core".equals(model.getArtifactId())) {
                    languages.add(model);
                } else if (!core && !"camel-core".equals(model.getArtifactId())) {
                    languages.add(model);
                }
            }

            // update the big readme file in the core/components dir
            File file;
            if (core) {
                file = new File(readmeCoreDir, "readme.adoc");
            } else {
                file = new File(readmeComponentsDir, "readme.adoc");
            }

            // update regular data formats
            boolean exists = file.exists();
            String changed = templateLanguages(languages);
            boolean updated = updateLanguages(file, changed);

            if (updated) {
                getLog().info("Updated readme.adoc file: " + file);
            } else if (exists) {
                getLog().debug("No changes to readme.adoc file: " + file);
            } else {
                getLog().warn("No readme.adoc file: " + file);
            }

        } catch (IOException e) {
            throw new MojoFailureException("Error due " + e.getMessage(), e);
        }
    }

    private String templateEips(List<EipModel> models) throws MojoExecutionException {
        try {
            String template = loadText(ReadmeComponentMojo.class.getClassLoader().getResourceAsStream("readme-eips.mvel"));
            Map<String, Object> map = new HashMap<>();
            map.put("eips", models);
            String out = (String) TemplateRuntime.eval(template, map);
            return out;
        } catch (Exception e) {
            throw new MojoExecutionException("Error processing mvel template. Reason: " + e, e);
        }
    }

    private String templateComponents(List<ComponentModel> models) throws MojoExecutionException {
        try {
            String template = loadText(ReadmeComponentMojo.class.getClassLoader().getResourceAsStream("readme-components.mvel"));
            Map<String, Object> map = new HashMap<>();
            map.put("components", models);
            String out = (String) TemplateRuntime.eval(template, map);
            return out;
        } catch (Exception e) {
            throw new MojoExecutionException("Error processing mvel template. Reason: " + e, e);
        }
    }

    private String templateDataFormats(List<DataFormatModel> models) throws MojoExecutionException {
        try {
            String template = loadText(ReadmeComponentMojo.class.getClassLoader().getResourceAsStream("readme-dataformats.mvel"));
            Map<String, Object> map = new HashMap<>();
            map.put("dataformats", models);
            String out = (String) TemplateRuntime.eval(template, map);
            return out;
        } catch (Exception e) {
            throw new MojoExecutionException("Error processing mvel template. Reason: " + e, e);
        }
    }

    private String templateLanguages(List<LanguageModel> models) throws MojoExecutionException {
        try {
            String template = loadText(ReadmeComponentMojo.class.getClassLoader().getResourceAsStream("readme-languages.mvel"));
            Map<String, Object> map = new HashMap<>();
            map.put("languages", models);
            String out = (String) TemplateRuntime.eval(template, map);
            return out;
        } catch (Exception e) {
            throw new MojoExecutionException("Error processing mvel template. Reason: " + e, e);
        }
    }

    private boolean updateEips(File file, String changed) throws MojoExecutionException {
        if (!file.exists()) {
            return false;
        }

        try {
            String text = loadText(new FileInputStream(file));

            String existing = StringHelper.between(text, "// eips: START", "// eips: END");
            if (existing != null) {
                // remove leading line breaks etc
                existing = existing.trim();
                changed = changed.trim();
                if (existing.equals(changed)) {
                    return false;
                } else {
                    String before = StringHelper.before(text, "// eips: START");
                    String after = StringHelper.after(text, "// eips: END");
                    text = before + "// eips: START\n" + changed + "\n// eips: END" + after;
                    writeText(file, text);
                    return true;
                }
            } else {
                getLog().warn("Cannot find markers in file " + file);
                getLog().warn("Add the following markers");
                getLog().warn("\t// eips: START");
                getLog().warn("\t// eips: END");
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

            String existing = StringHelper.between(text, "// components: START", "// components: END");
            if (existing != null) {
                // remove leading line breaks etc
                existing = existing.trim();
                changed = changed.trim();
                if (existing.equals(changed)) {
                    return false;
                } else {
                    String before = StringHelper.before(text, "// components: START");
                    String after = StringHelper.after(text, "// components: END");
                    text = before + "// components: START\n" + changed + "\n// components: END" + after;
                    writeText(file, text);
                    return true;
                }
            } else {
                getLog().warn("Cannot find markers in file " + file);
                getLog().warn("Add the following markers");
                getLog().warn("\t// components: START");
                getLog().warn("\t// components: END");
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

            String existing = StringHelper.between(text, "// dataformats: START", "// dataformats: END");
            if (existing != null) {
                // remove leading line breaks etc
                existing = existing.trim();
                changed = changed.trim();
                if (existing.equals(changed)) {
                    return false;
                } else {
                    String before = StringHelper.before(text, "// dataformats: START");
                    String after = StringHelper.after(text, "// dataformats: END");
                    text = before + "// dataformats: START\n" + changed + "\n// dataformats: END" + after;
                    writeText(file, text);
                    return true;
                }
            } else {
                getLog().warn("Cannot find markers in file " + file);
                getLog().warn("Add the following markers");
                getLog().warn("\t// dataformats: START");
                getLog().warn("\t// dataformats: END");
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

            String existing = StringHelper.between(text, "// languages: START", "// languages: END");
            if (existing != null) {
                // remove leading line breaks etc
                existing = existing.trim();
                changed = changed.trim();
                if (existing.equals(changed)) {
                    return false;
                } else {
                    String before = StringHelper.before(text, "// languages: START");
                    String after = StringHelper.after(text, "// languages: END");
                    text = before + "// languages: START\n" + changed + "\n// languages: END" + after;
                    writeText(file, text);
                    return true;
                }
            } else {
                getLog().warn("Cannot find markers in file " + file);
                getLog().warn("Add the following markers");
                getLog().warn("\t// languages: START");
                getLog().warn("\t// languages: END");
                return false;
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error reading file " + file + " Reason: " + e, e);
        }
    }

    private static class EipComparator implements Comparator<EipModel> {

        @Override
        public int compare(EipModel o1, EipModel o2) {
            // lets sort by title
            return o1.getTitle().compareToIgnoreCase(o2.getTitle());
        }
    }

    private static class ComponentComparator implements Comparator<ComponentModel> {

        @Override
        public int compare(ComponentModel o1, ComponentModel o2) {
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

    private EipModel generateEipModel(String json) {
        List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("model", json, false);

        EipModel eip = new EipModel();
        eip.setName(JSonSchemaHelper.getSafeValue("name", rows));
        eip.setTitle(JSonSchemaHelper.getSafeValue("title", rows));
        eip.setDescription(JSonSchemaHelper.getSafeValue("description", rows));
        eip.setJavaType(JSonSchemaHelper.getSafeValue("javaType", rows));
        eip.setLabel(JSonSchemaHelper.getSafeValue("label", rows));
        eip.setDeprecated("true".equals(JSonSchemaHelper.getSafeValue("deprecated", rows)));
        eip.setInput("true".equals(JSonSchemaHelper.getSafeValue("input", rows)));
        eip.setOutput("true".equals(JSonSchemaHelper.getSafeValue("output", rows)));

        return eip;
    }

    private ComponentModel generateComponentModel(String json) {
        List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("component", json, false);

        ComponentModel component = new ComponentModel();
        component.setScheme(JSonSchemaHelper.getSafeValue("scheme", rows));
        component.setSyntax(JSonSchemaHelper.getSafeValue("syntax", rows));
        component.setAlternativeSyntax(JSonSchemaHelper.getSafeValue("alternativeSyntax", rows));
        component.setAlternativeSchemes(JSonSchemaHelper.getSafeValue("alternativeSchemes", rows));
        component.setTitle(JSonSchemaHelper.getSafeValue("title", rows));
        component.setDescription(JSonSchemaHelper.getSafeValue("description", rows));
        component.setLabel(JSonSchemaHelper.getSafeValue("label", rows));
        component.setDeprecated(JSonSchemaHelper.getSafeValue("deprecated", rows));
        component.setConsumerOnly(JSonSchemaHelper.getSafeValue("consumerOnly", rows));
        component.setProducerOnly(JSonSchemaHelper.getSafeValue("producerOnly", rows));
        component.setJavaType(JSonSchemaHelper.getSafeValue("javaType", rows));
        component.setGroupId(JSonSchemaHelper.getSafeValue("groupId", rows));
        component.setArtifactId(JSonSchemaHelper.getSafeValue("artifactId", rows));
        component.setVersion(JSonSchemaHelper.getSafeValue("version", rows));

        return component;
    }

    private DataFormatModel generateDataFormatModel(String json) {
        List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("dataformat", json, false);

        DataFormatModel dataFormat = new DataFormatModel();
        dataFormat.setName(JSonSchemaHelper.getSafeValue("name", rows));
        dataFormat.setTitle(JSonSchemaHelper.getSafeValue("title", rows));
        dataFormat.setModelName(JSonSchemaHelper.getSafeValue("modelName", rows));
        dataFormat.setDescription(JSonSchemaHelper.getSafeValue("description", rows));
        dataFormat.setLabel(JSonSchemaHelper.getSafeValue("label", rows));
        dataFormat.setDeprecated(JSonSchemaHelper.getSafeValue("deprecated", rows));
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
        language.setLabel(JSonSchemaHelper.getSafeValue("label", rows));
        language.setDeprecated(JSonSchemaHelper.getSafeValue("deprecated", rows));
        language.setJavaType(JSonSchemaHelper.getSafeValue("javaType", rows));
        language.setGroupId(JSonSchemaHelper.getSafeValue("groupId", rows));
        language.setArtifactId(JSonSchemaHelper.getSafeValue("artifactId", rows));
        language.setVersion(JSonSchemaHelper.getSafeValue("version", rows));

        return language;
    }

}
