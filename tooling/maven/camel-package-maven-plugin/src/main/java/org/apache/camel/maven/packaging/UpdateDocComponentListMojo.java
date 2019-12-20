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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.camel.maven.packaging.model.ComponentModel;
import org.apache.camel.maven.packaging.model.DataFormatModel;
import org.apache.camel.maven.packaging.model.EipModel;
import org.apache.camel.maven.packaging.model.LanguageModel;
import org.apache.camel.maven.packaging.model.OtherModel;
import org.apache.camel.tooling.util.Strings;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.mvel2.templates.TemplateRuntime;

import static org.apache.camel.tooling.util.PackageHelper.loadText;
import static org.apache.camel.tooling.util.PackageHelper.writeText;
import static org.apache.camel.tooling.util.JSonSchemaHelper.getSafeValue;
import static org.apache.camel.tooling.util.JSonSchemaHelper.parseJsonSchema;

/**
 * Updates the website docs with the component list to be up to date with all the artifacts that Apache Camel ships.
 */
@Mojo(name = "update-doc-component-list", threadSafe = true)
public class UpdateDocComponentListMojo extends AbstractMojo {

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
     * The website doc for components
     */
    @Parameter(defaultValue = "${project.directory}/../../../docs/components/modules/ROOT/pages/index.adoc")
    protected File websiteDocFile;

    /**
     * Maven ProjectHelper.
     */
    @Component
    private MavenProjectHelper projectHelper;

    /**
     * Execute goal.
     *
     * @throws MojoExecutionException execution of the main class or one of the
     *                                threads it generated failed.
     * @throws MojoFailureException   something bad happened...
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        executeComponentsList();
        executeDataFormatsList();
        executeLanguagesList();
        executeOthersReadme();
    }

    protected void executeComponentsList() throws MojoExecutionException, MojoFailureException {
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

                    // special for camel-mail where we want to refer its imap scheme to mail so its mail.adoc in the doc link
                    if ("imap".equals(model.getScheme())) {
                        model.setScheme("mail");
                        model.setTitle("Mail");
                    }
                }
            }

            // sort the models
            models.sort(new ComponentComparator());

            // how many different artifacts
            long count = models.stream()
                    .map(ComponentModel::getArtifactId)
                    .distinct()
                    .count();

            // how many deprecated
            long deprecated = models.stream()
                    .filter(c -> "true".equals(c.getDeprecated()))
                    .count();

            // update doc in the website dir
            File file = websiteDocFile;
            boolean exists = file.exists();
            String changed = templateComponents(models, count, deprecated);
            boolean updated = updateComponents(file, changed);
            if (updated) {
                getLog().info("Updated website doc file: " + file);
            } else if (exists) {
                getLog().debug("No changes to website doc file: " + file);
            } else {
                getLog().warn("No website doc file: " + file);
            }
        } catch (IOException e) {
            throw new MojoFailureException("Error due " + e.getMessage(), e);
        }
    }

    protected void executeOthersReadme() throws MojoExecutionException, MojoFailureException {
        Set<File> otherFiles = new TreeSet<>();

        if (othersDir != null && othersDir.isDirectory()) {
            File[] files = othersDir.listFiles();
            if (files != null) {
                otherFiles.addAll(Arrays.asList(files));
            }
        }

        try {
            List<OtherModel> others = new ArrayList<>();
            for (File file : otherFiles) {
                String json = loadText(file);
                OtherModel model = generateOtherModel(json);
                others.add(model);
            }

            // sort the models
            others.sort(new OtherComparator());

            // how many different artifacts
            long count = others.stream()
                    .map(OtherModel::getArtifactId)
                    .distinct()
                    .count();

            // how many deprecated
            long deprecated = others.stream()
                    .filter(o -> "true".equals(o.getDeprecated()))
                    .count();

            // update doc in the website dir
            File file = websiteDocFile;
            boolean exists = file.exists();
            String changed = templateOthers(others, count, deprecated);
            boolean updated = updateOthers(file, changed);
            if (updated) {
                getLog().info("Updated website doc file: " + file);
            } else if (exists) {
                getLog().debug("No changes to website doc file: " + file);
            } else {
                getLog().warn("No website doc file: " + file);
            }

        } catch (IOException e) {
            throw new MojoFailureException("Error due " + e.getMessage(), e);
        }
    }

    protected void executeDataFormatsList() throws MojoExecutionException, MojoFailureException {
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

                // special for bindy as we have one common file
                if (model.getName().startsWith("bindy")) {
                    model.setName("bindy");
                }

                models.add(model);
            }

            // sort the models
            models.sort(new DataFormatComparator());

            // how many different artifacts
            long count = models.stream()
                    .map(DataFormatModel::getArtifactId)
                    .distinct()
                    .count();

            // how many deprecated
            long deprecated = models.stream()
                    .filter(m -> "true".equals(m.getDeprecated()))
                    .count();

            // update doc in the website dir
            File file = websiteDocFile;
            boolean exists = file.exists();
            String changed = templateDataFormats(models, count, deprecated);
            boolean updated = updateDataFormats(file, changed);
            if (updated) {
                getLog().info("Updated website doc file: " + file);
            } else if (exists) {
                getLog().debug("No changes to website doc file: " + file);
            } else {
                getLog().warn("No website doc file: " + file);
            }

        } catch (IOException e) {
            throw new MojoFailureException("Error due " + e.getMessage(), e);
        }
    }

    protected void executeLanguagesList() throws MojoExecutionException, MojoFailureException {
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

            // sort the models
            models.sort(new LanguageComparator());

            // how many different artifacts
            long count = models.stream()
                    .map(LanguageModel::getArtifactId)
                    .distinct()
                    .count();

            // how many deprecated
            long deprecated = models.stream()
                    .filter(l -> "true".equals(l.getDeprecated()))
                    .count();

            // update doc in the website dir
            File file = websiteDocFile;
            boolean exists = file.exists();
            String changed = templateLanguages(models, count, deprecated);
            boolean updated = updateLanguages(file, changed);
            if (updated) {
                getLog().info("Updated website doc file: " + file);
            } else if (exists) {
                getLog().debug("No changes to website doc file: " + file);
            } else {
                getLog().warn("No website doc file: " + file);
            }

        } catch (IOException e) {
            throw new MojoFailureException("Error due " + e.getMessage(), e);
        }
    }

    private String templateComponents(List<ComponentModel> models, long artifacts, long deprecated) throws MojoExecutionException {
        try {
            String template = loadResource("website-components-list.mvel");
            Map<String, Object> map = new HashMap<>();
            map.put("components", models);
            map.put("numberOfArtifacts", artifacts);
            map.put("numberOfDeprecated", deprecated);
            String out = (String) TemplateRuntime.eval(template, map, Collections.singletonMap("util", MvelHelper.INSTANCE));
            return out;
        } catch (Exception e) {
            throw new MojoExecutionException("Error processing mvel template. Reason: " + e, e);
        }
    }

    private String templateOthers(List<OtherModel> models, long artifacts, long deprecated) throws MojoExecutionException {
        try {
            String template = loadResource("website-others-list.mvel");
            Map<String, Object> map = new HashMap<>();
            map.put("others", models);
            map.put("numberOfArtifacts", artifacts);
            map.put("numberOfDeprecated", deprecated);
            String out = (String) TemplateRuntime.eval(template, map, Collections.singletonMap("util", MvelHelper.INSTANCE));
            return out;
        } catch (Exception e) {
            throw new MojoExecutionException("Error processing mvel template. Reason: " + e, e);
        }
    }

    private String templateDataFormats(List<DataFormatModel> models, long artifacts, long deprecated) throws MojoExecutionException {
        try {
            String template = loadResource("website-dataformats-list.mvel");
            Map<String, Object> map = new HashMap<>();
            map.put("dataformats", models);
            map.put("numberOfArtifacts", artifacts);
            map.put("numberOfDeprecated", deprecated);
            String out = (String) TemplateRuntime.eval(template, map, Collections.singletonMap("util", MvelHelper.INSTANCE));
            return out;
        } catch (Exception e) {
            throw new MojoExecutionException("Error processing mvel template. Reason: " + e, e);
        }
    }

    private String templateLanguages(List<LanguageModel> models, long artifacts, long deprecated) throws MojoExecutionException {
        try {
            String template = loadResource("website-languages-list.mvel");
            Map<String, Object> map = new HashMap<>();
            map.put("languages", models);
            map.put("numberOfArtifacts", artifacts);
            map.put("numberOfDeprecated", deprecated);
            String out = (String) TemplateRuntime.eval(template, map, Collections.singletonMap("util", MvelHelper.INSTANCE));
            return out;
        } catch (Exception e) {
            throw new MojoExecutionException("Error processing mvel template. Reason: " + e, e);
        }
    }

    private String loadResource(String name) throws IOException {
        return loadText(UpdateReadmeMojo.class.getClassLoader().getResourceAsStream(name));
    }

    private boolean updateComponents(File file, String changed) throws MojoExecutionException {
        if (!file.exists()) {
            return false;
        }

        try {
            String text = loadText(file);

            String existing = Strings.between(text, "// components: START", "// components: END");
            if (existing != null) {
                // remove leading line breaks etc
                existing = existing.trim();
                changed = changed.trim();
                if (existing.equals(changed)) {
                    return false;
                } else {
                    String before = Strings.before(text, "// components: START");
                    String after = Strings.after(text, "// components: END");
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

    private boolean updateOthers(File file, String changed) throws MojoExecutionException {
        if (!file.exists()) {
            return false;
        }

        try {
            String text = loadText(file);

            String existing = Strings.between(text, "// others: START", "// others: END");
            if (existing != null) {
                // remove leading line breaks etc
                existing = existing.trim();
                changed = changed.trim();
                if (existing.equals(changed)) {
                    return false;
                } else {
                    String before = Strings.before(text, "// others: START");
                    String after = Strings.after(text, "// others: END");
                    text = before + "// others: START\n" + changed + "\n// others: END" + after;
                    writeText(file, text);
                    return true;
                }
            } else {
                getLog().warn("Cannot find markers in file " + file);
                getLog().warn("Add the following markers");
                getLog().warn("\t// others: START");
                getLog().warn("\t// others: END");
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

            String existing = Strings.between(text, "// dataformats: START", "// dataformats: END");
            if (existing != null) {
                // remove leading line breaks etc
                existing = existing.trim();
                changed = changed.trim();
                if (existing.equals(changed)) {
                    return false;
                } else {
                    String before = Strings.before(text, "// dataformats: START");
                    String after = Strings.after(text, "// dataformats: END");
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
            String text = loadText(file);

            String existing = Strings.between(text, "// languages: START", "// languages: END");
            if (existing != null) {
                // remove leading line breaks etc
                existing = existing.trim();
                changed = changed.trim();
                if (existing.equals(changed)) {
                    return false;
                } else {
                    String before = Strings.before(text, "// languages: START");
                    String after = Strings.after(text, "// languages: END");
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

    private EipModel generateEipModel(String json) {
        List<Map<String, String>> rows = parseJsonSchema("model", json, false);

        EipModel eip = new EipModel();
        eip.setName(getSafeValue("name", rows));
        eip.setTitle(getSafeValue("title", rows));
        eip.setDescription(getSafeValue("description", rows));
        eip.setJavaType(getSafeValue("javaType", rows));
        eip.setLabel(getSafeValue("label", rows));
        eip.setDeprecated("true".equals(getSafeValue("deprecated", rows)));
        eip.setDeprecationNote(getSafeValue("deprecationNote", rows));
        eip.setInput("true".equals(getSafeValue("input", rows)));
        eip.setOutput("true".equals(getSafeValue("output", rows)));

        return eip;
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
        List<Map<String, String>> rows = parseJsonSchema("other", json, false);

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
        List<Map<String, String>> rows = parseJsonSchema("dataformat", json, false);

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
        List<Map<String, String>> rows = parseJsonSchema("language", json, false);

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
