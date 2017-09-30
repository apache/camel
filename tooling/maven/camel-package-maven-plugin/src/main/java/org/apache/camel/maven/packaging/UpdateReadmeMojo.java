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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.camel.maven.packaging.model.ComponentModel;
import org.apache.camel.maven.packaging.model.ComponentOptionModel;
import org.apache.camel.maven.packaging.model.DataFormatModel;
import org.apache.camel.maven.packaging.model.DataFormatOptionModel;
import org.apache.camel.maven.packaging.model.EipModel;
import org.apache.camel.maven.packaging.model.EipOptionModel;
import org.apache.camel.maven.packaging.model.EndpointOptionModel;
import org.apache.camel.maven.packaging.model.LanguageModel;
import org.apache.camel.maven.packaging.model.LanguageOptionModel;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.mvel2.templates.TemplateRuntime;
import org.sonatype.plexus.build.incremental.BuildContext;

import static org.apache.camel.maven.packaging.JSonSchemaHelper.*;
import static org.apache.camel.maven.packaging.PackageHelper.loadText;
import static org.apache.camel.maven.packaging.PackageHelper.writeText;
import static org.apache.camel.maven.packaging.StringHelper.isEmpty;

/**
 * Generate or updates the component/dataformat/language/eip readme.md and .adoc files in the project root directory.
 *
 * @goal update-readme
 */
public class UpdateReadmeMojo extends AbstractMojo {

    /**
     * The maven project.
     *
     * @parameter property="project"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /**
     * The project build directory
     *
     * @parameter default-value="${project.build.directory}"
     */
    protected File buildDir;

    /**
     * The documentation directory
     *
     * @parameter default-value="${basedir}/src/main/docs"
     */
    protected File docDir;

    /**
     * The documentation directory
     *
     * @parameter default-value="${basedir}/src/main/docs/eips"
     */
    protected File eipDocDir;

    /**
     * Whether to fail the build fast if any Warnings was detected.
     *
     * @parameter
     */
    protected Boolean failFast;

    /**
     * build context to check changed files and mark them for refresh (used for
     * m2e compatibility)
     *
     * @component
     * @readonly
     */
    private BuildContext buildContext;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        executeComponent();
        executeDataFormat();
        executeLanguage();
        executeEips();
    }

    private void executeComponent() throws MojoExecutionException, MojoFailureException {
        // find the component names
        List<String> componentNames = findComponentNames();

        final Set<File> jsonFiles = new TreeSet<File>();
        PackageHelper.findJsonFiles(buildDir, jsonFiles, new PackageHelper.CamelComponentsModelFilter());

        // only if there is components we should update the documentation files
        if (!componentNames.isEmpty()) {
            getLog().debug("Found " + componentNames.size() + " components");
            for (String componentName : componentNames) {
                String json = loadComponentJson(jsonFiles, componentName);
                if (json != null) {
                    // special for some components
                    componentName = asComponentName(componentName);

                    File file = new File(docDir, componentName + "-component.adoc");

                    ComponentModel model = generateComponentModel(componentName, json);
                    String title = asComponentTitle(model.getScheme(), model.getTitle());
                    model.setTitle(title);

                    // we only want the first scheme as the alternatives do not have their own readme file
                    if (!isEmpty(model.getAlternativeSchemes())) {
                        String first = model.getAlternativeSchemes().split(",")[0];
                        if (!model.getScheme().equals(first)) {
                            continue;
                        }
                    }

                    String docTitle = model.getTitle() + " Component";
                    boolean deprecated = "true".equals(model.getDeprecated());
                    if (deprecated) {
                        docTitle += " (deprecated)";
                    }

                    boolean exists = file.exists();
                    boolean updated;
                    updated = updateTitles(file, docTitle);
                    updated |= updateAvailableFrom(file, model.getFirstVersion());

                    // resolvePropertyPlaceholders is an option which only make sense to use if the component has other options
                    boolean hasOptions = model.getComponentOptions().stream().anyMatch(o -> !o.getName().equals("resolvePropertyPlaceholders"));
                    if (!hasOptions) {
                        model.getComponentOptions().clear();
                    }

                    String options = templateComponentOptions(model);
                    updated |= updateComponentOptions(file, options);

                    options = templateEndpointOptions(model);
                    updated |= updateEndpointOptions(file, options);

                    if (updated) {
                        getLog().info("Updated doc file: " + file);
                    } else if (exists) {
                        getLog().debug("No changes to doc file: " + file);
                    } else {
                        getLog().warn("No component doc file: " + file);
                        if (isFailFast()) {
                            throw new MojoExecutionException("Failed build due failFast=true");
                        }
                    }
                }
            }
        }
    }

    private void executeDataFormat() throws MojoExecutionException, MojoFailureException {
        // find the dataformat names
        List<String> dataFormatNames = findDataFormatNames();

        final Set<File> jsonFiles = new TreeSet<File>();
        PackageHelper.findJsonFiles(buildDir, jsonFiles, new PackageHelper.CamelComponentsModelFilter());

        // only if there is dataformat we should update the documentation files
        if (!dataFormatNames.isEmpty()) {
            getLog().debug("Found " + dataFormatNames.size() + " dataformats");
            for (String dataFormatName : dataFormatNames) {
                String json = loadDataFormatJson(jsonFiles, dataFormatName);
                if (json != null) {
                    // special for some data formats
                    dataFormatName = asDataFormatName(dataFormatName);

                    File file = new File(docDir, dataFormatName + "-dataformat.adoc");

                    DataFormatModel model = generateDataFormatModel(dataFormatName, json);
                    String title = asDataFormatTitle(model.getName(), model.getTitle());
                    model.setTitle(title);

                    String docTitle = model.getTitle() + " DataFormat";
                    boolean deprecated = "true".equals(model.getDeprecated());
                    if (deprecated) {
                        docTitle += " (deprecated)";
                    }

                    boolean exists = file.exists();
                    boolean updated;
                    updated = updateTitles(file, docTitle);
                    updated |= updateAvailableFrom(file, model.getFirstVersion());

                    String options = templateDataFormatOptions(model);
                    updated |= updateDataFormatOptions(file, options);

                    if (updated) {
                        getLog().info("Updated doc file: " + file);
                    } else if (exists) {
                        getLog().debug("No changes to doc file: " + file);
                    } else {
                        getLog().warn("No dataformat doc file: " + file);
                        if (isFailFast()) {
                            throw new MojoExecutionException("Failed build due failFast=true");
                        }
                    }
                }
            }
        }
    }

    private static String asComponentName(String name) {
        // special for some components which share the same readme file
        if (name.equals("imap") || name.equals("imaps") || name.equals("pop3") || name.equals("pop3s") || name.equals("smtp") || name.equals("smtps")) {
            return "mail";
        } else {
            return name;
        }
    }

    private void executeLanguage() throws MojoExecutionException, MojoFailureException {
        // find the language names
        List<String> languageNames = findLanguageNames();

        final Set<File> jsonFiles = new TreeSet<File>();
        PackageHelper.findJsonFiles(buildDir, jsonFiles, new PackageHelper.CamelComponentsModelFilter());

        // only if there is language we should update the documentation files
        if (!languageNames.isEmpty()) {
            getLog().debug("Found " + languageNames.size() + " languages");
            for (String languageName : languageNames) {
                String json = loadLanguageJson(jsonFiles, languageName);
                if (json != null) {
                    File file = new File(docDir, languageName + "-language.adoc");

                    LanguageModel model = generateLanguageModel(languageName, json);

                    String docTitle = model.getTitle() + " Language";
                    boolean deprecated = "true".equals(model.getDeprecated());
                    if (deprecated) {
                        docTitle += " (deprecated)";
                    }

                    boolean exists = file.exists();
                    boolean updated;
                    updated = updateTitles(file, docTitle);
                    updated |= updateAvailableFrom(file, model.getFirstVersion());

                    String options = templateLanguageOptions(model);
                    updated |= updateLanguageOptions(file, options);

                    if (updated) {
                        getLog().info("Updated doc file: " + file);
                    } else if (exists) {
                        getLog().debug("No changes to doc file: " + file);
                    } else {
                        getLog().warn("No language doc file: " + file);
                        if (isFailFast()) {
                            throw new MojoExecutionException("Failed build due failFast=true");
                        }
                    }
                }
            }
        }
    }

    private void executeEips() throws MojoExecutionException, MojoFailureException {
        // only run if in camel-core
        String currentDir = Paths.get(".").normalize().toAbsolutePath().toString();
        if (!currentDir.endsWith("camel-core")) {
            return;
        }

        final Set<File> jsonFiles = new TreeSet<File>();

        // find all json files in camel-core
        File coreDir = new File(".");
        if (coreDir.isDirectory()) {
            File target = new File(coreDir, "target/classes/org/apache/camel/model");
            PackageHelper.findJsonFiles(target, jsonFiles, new PackageHelper.CamelComponentsModelFilter());
        }

        // only if there is dataformat we should update the documentation files
        if (!jsonFiles.isEmpty()) {
            getLog().debug("Found " + jsonFiles.size() + " eips");
            for (File jsonFile : jsonFiles) {
                String json = loadEipJson(jsonFile);
                if (json != null) {
                    EipModel model = generateEipModel(json);
                    String title = asEipTitle(model.getName(), model.getTitle());
                    model.setTitle(title);

                    String eipName = model.getName();

                    // we only want actual EIPs from the models
                    if (!model.getLabel().startsWith("eip")) {
                        continue;
                    }

                    File file = new File(eipDocDir, eipName + "-eip.adoc");

                    String docTitle = model.getTitle() + " EIP";
                    boolean deprecated = model.isDeprecated();
                    if (deprecated) {
                        docTitle += " (deprecated)";
                    }

                    boolean exists = file.exists();
                    boolean updated;
                    updated = updateTitles(file, docTitle);

                    String options = templateEipOptions(model);
                    updated |= updateEipOptions(file, options);

                    if (updated) {
                        getLog().info("Updated doc file: " + file);
                    } else if (exists) {
                        getLog().debug("No changes to doc file: " + file);
                    } else {
                        getLog().warn("No eip doc file: " + file);
                        if (isFailFast()) {
                            throw new MojoExecutionException("Failed build due failFast=true");
                        }
                    }
                }
            }
        }
    }

    private static String asComponentTitle(String name, String title) {
        // special for some components which share the same readme file
        if (name.equals("imap") || name.equals("imaps") || name.equals("pop3") || name.equals("pop3s") || name.equals("smtp") || name.equals("smtps")) {
            return "Mail";
        } else {
            return title;
        }
    }

    private static String asDataFormatName(String name) {
        // special for some dataformats which share the same readme file
        if (name.startsWith("bindy")) {
            return "bindy";
        } else {
            return name;
        }
    }

    private static String asEipName(String name) {
        return name;
    }

    private static String asDataFormatTitle(String name, String title) {
        // special for some dataformats which share the same readme file
        if (name.startsWith("bindy")) {
            return "Bindy";
        } else {
            return title;
        }
    }

    private static String asEipTitle(String name, String title) {
        return title;
    }

    private boolean updateTitles(File file, String title) throws MojoExecutionException {
        if (!file.exists()) {
            return false;
        }

        boolean updated = false;

        try {
            List<String> newLines = new ArrayList<>();

            String text = loadText(new FileInputStream(file));
            String[] lines = text.split("\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];

                if (i == 0) {
                    // first line is the title to make the text less noisy we use level 2
                    String newLine = "== " + title;
                    newLines.add(newLine);
                    updated = !line.equals(newLine);
                    continue;
                }

                // use single line headers with # as level instead of the cumbersome adoc weird style
                if (line.startsWith("^^^") || line.startsWith("~~~") || line.startsWith("+++")) {
                    String level = line.startsWith("+++") ? "====" : "===";

                    // transform legacy heading into new style
                    int idx = newLines.size() - 1;
                    String prev = newLines.get(idx);

                    newLines.set(idx, level + " " + prev);

                    // okay if 2nd-prev line is a [[title]] we need to remove that too
                    // so we have nice clean sub titles
                    idx = newLines.size() - 2;
                    if (idx >= 0) {
                        prev = newLines.get(idx);
                        if (prev.startsWith("[[")) {
                            // remove
                            newLines.remove(idx);
                        }
                    }

                    updated = true;
                } else {
                    // okay normal text so just add it
                    newLines.add(line);
                }
            }


            if (updated) {
                // build the new updated text
                String newText = newLines.stream().collect(Collectors.joining("\n"));
                writeText(file, newText);
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error reading file " + file + " Reason: " + e, e);
        }

        return updated;
    }

    private boolean updateAvailableFrom(File file, String firstVersion) throws MojoExecutionException {
        if (firstVersion == null || !file.exists()) {
            return false;
        }

        // cut last digit so its not 2.18.0 but 2.18
        String[] parts = firstVersion.split("\\.");
        if (parts.length == 3 && parts[2].equals("0")) {
            firstVersion = parts[0] + "." + parts[1];
        }

        boolean updated = false;

        try {
            String text = loadText(new FileInputStream(file));

            String[] lines = text.split("\n");

            List<String> newLines = new ArrayList<>();

            // copy over to all new lines
            newLines.addAll(Arrays.asList(lines));

            // check the first four lines
            boolean title = lines[0].startsWith("##") || lines[0].startsWith("==");
            boolean empty = lines[1].trim().isEmpty();
            boolean availableFrom = lines[2].trim().contains("Available as of") || lines[2].trim().contains("Available in");
            boolean empty2 = lines[3].trim().isEmpty();

            if (title && empty && availableFrom) {
                String newLine = "*Available as of Camel version " + firstVersion + "*";
                if (!newLine.equals(lines[2])) {
                    newLines.set(2, newLine);
                    updated = true;
                }
                if (!empty2) {
                    newLines.add(3, "");
                    updated = true;
                }
            } else if (!availableFrom) {
                String newLine = "*Available as of Camel version " + firstVersion + "*";
                newLines.add(2, newLine);
                newLines.add(3, "");
                updated = true;
            }

            if (updated) {
                // build the new updated text
                String newText = newLines.stream().collect(Collectors.joining("\n"));
                writeText(file, newText);
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error reading file " + file + " Reason: " + e, e);
        }

        return updated;
    }

    private boolean updateComponentOptions(File file, String changed) throws MojoExecutionException {
        if (!file.exists()) {
            return false;
        }

        try {
            String text = loadText(new FileInputStream(file));

            String existing = StringHelper.between(text, "// component options: START", "// component options: END");
            if (existing != null) {
                // remove leading line breaks etc
                existing = existing.trim();
                changed = changed.trim();
                if (existing.equals(changed)) {
                    return false;
                } else {
                    String before = StringHelper.before(text, "// component options: START");
                    String after = StringHelper.after(text, "// component options: END");
                    text = before + "// component options: START\n" + changed + "\n// component options: END" + after;
                    writeText(file, text);
                    return true;
                }
            } else {
                getLog().warn("Cannot find markers in file " + file);
                getLog().warn("Add the following markers");
                getLog().warn("\t// component options: START");
                getLog().warn("\t// component options: END");
                if (isFailFast()) {
                    throw new MojoExecutionException("Failed build due failFast=true");
                }
                return false;
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error reading file " + file + " Reason: " + e, e);
        }
    }

    private boolean updateEndpointOptions(File file, String changed) throws MojoExecutionException {
        if (!file.exists()) {
            return false;
        }

        try {
            String text = loadText(new FileInputStream(file));

            String existing = StringHelper.between(text, "// endpoint options: START", "// endpoint options: END");
            if (existing != null) {
                // remove leading line breaks etc
                existing = existing.trim();
                changed = changed.trim();
                if (existing.equals(changed)) {
                    return false;
                } else {
                    String before = StringHelper.before(text, "// endpoint options: START");
                    String after = StringHelper.after(text, "// endpoint options: END");
                    text = before + "// endpoint options: START\n" + changed + "\n// endpoint options: END" + after;
                    writeText(file, text);
                    return true;
                }
            } else {
                getLog().warn("Cannot find markers in file " + file);
                getLog().warn("Add the following markers");
                getLog().warn("\t// endpoint options: START");
                getLog().warn("\t// endpoint options: END");
                if (isFailFast()) {
                    throw new MojoExecutionException("Failed build due failFast=true");
                }
                return false;
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error reading file " + file + " Reason: " + e, e);
        }
    }

    private boolean updateDataFormatOptions(File file, String changed) throws MojoExecutionException {
        if (!file.exists()) {
            return false;
        }

        try {
            String text = loadText(new FileInputStream(file));

            String existing = StringHelper.between(text, "// dataformat options: START", "// dataformat options: END");
            if (existing != null) {
                // remove leading line breaks etc
                existing = existing.trim();
                changed = changed.trim();
                if (existing.equals(changed)) {
                    return false;
                } else {
                    String before = StringHelper.before(text, "// dataformat options: START");
                    String after = StringHelper.after(text, "// dataformat options: END");
                    text = before + "// dataformat options: START\n" + changed + "\n// dataformat options: END" + after;
                    writeText(file, text);
                    return true;
                }
            } else {
                getLog().warn("Cannot find markers in file " + file);
                getLog().warn("Add the following markers");
                getLog().warn("\t// dataformat options: START");
                getLog().warn("\t// dataformat options: END");
                if (isFailFast()) {
                    throw new MojoExecutionException("Failed build due failFast=true");
                }
                return false;
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error reading file " + file + " Reason: " + e, e);
        }
    }

    private boolean updateLanguageOptions(File file, String changed) throws MojoExecutionException {
        if (!file.exists()) {
            return false;
        }

        try {
            String text = loadText(new FileInputStream(file));

            String existing = StringHelper.between(text, "// language options: START", "// language options: END");
            if (existing != null) {
                // remove leading line breaks etc
                existing = existing.trim();
                changed = changed.trim();
                if (existing.equals(changed)) {
                    return false;
                } else {
                    String before = StringHelper.before(text, "// language options: START");
                    String after = StringHelper.after(text, "// language options: END");
                    text = before + "// language options: START\n" + changed + "\n// language options: END" + after;
                    writeText(file, text);
                    return true;
                }
            } else {
                getLog().warn("Cannot find markers in file " + file);
                getLog().warn("Add the following markers");
                getLog().warn("\t// language options: START");
                getLog().warn("\t// language options: END");
                if (isFailFast()) {
                    throw new MojoExecutionException("Failed build due failFast=true");
                }
                return false;
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error reading file " + file + " Reason: " + e, e);
        }
    }

    private boolean updateEipOptions(File file, String changed) throws MojoExecutionException {
        if (!file.exists()) {
            return false;
        }

        try {
            String text = loadText(new FileInputStream(file));

            String existing = StringHelper.between(text, "// eip options: START", "// eip options: END");
            if (existing != null) {
                // remove leading line breaks etc
                existing = existing.trim();
                changed = changed.trim();
                if (existing.equals(changed)) {
                    return false;
                } else {
                    String before = StringHelper.before(text, "// eip options: START");
                    String after = StringHelper.after(text, "// eip options: END");
                    text = before + "// eip options: START\n" + changed + "\n// eip options: END" + after;
                    writeText(file, text);
                    return true;
                }
            } else {
                getLog().warn("Cannot find markers in file " + file);
                getLog().warn("Add the following markers");
                getLog().warn("\t// eip options: START");
                getLog().warn("\t// eip options: END");
                if (isFailFast()) {
                    throw new MojoExecutionException("Failed build due failFast=true");
                }
                return false;
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error reading file " + file + " Reason: " + e, e);
        }
    }

    private String loadComponentJson(Set<File> jsonFiles, String componentName) {
        try {
            for (File file : jsonFiles) {
                if (file.getName().equals(componentName + ".json")) {
                    String json = loadText(new FileInputStream(file));
                    boolean isComponent = json.contains("\"kind\": \"component\"");
                    if (isComponent) {
                        return json;
                    }
                }
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }

    private String loadDataFormatJson(Set<File> jsonFiles, String dataFormatName) {
        try {
            for (File file : jsonFiles) {
                if (file.getName().equals(dataFormatName + ".json")) {
                    String json = loadText(new FileInputStream(file));
                    boolean isDataFormat = json.contains("\"kind\": \"dataformat\"");
                    if (isDataFormat) {
                        return json;
                    }
                }
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }

    private String loadLanguageJson(Set<File> jsonFiles, String languageName) {
        try {
            for (File file : jsonFiles) {
                if (file.getName().equals(languageName + ".json")) {
                    String json = loadText(new FileInputStream(file));
                    boolean isLanguage = json.contains("\"kind\": \"language\"");
                    if (isLanguage) {
                        return json;
                    }
                }
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }

    private String loadEipJson(File file) {
        try {
            String json = loadText(new FileInputStream(file));
            boolean isEip = json.contains("\"kind\": \"model\"");
            if (isEip) {
                return json;
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }

    private ComponentModel generateComponentModel(String componentName, String json) {
        List<Map<String, String>> rows = parseJsonSchema("component", json, false);

        ComponentModel component = new ComponentModel(true);
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

        String oldGroup = null;
        rows = parseJsonSchema("componentProperties", json, true);
        for (Map<String, String> row : rows) {
            ComponentOptionModel option = new ComponentOptionModel();
            option.setName(getSafeValue("name", row));
            option.setDisplayName(getSafeValue("displayName", row));
            option.setKind(getSafeValue("kind", row));
            option.setGroup(getSafeValue("group", row));
            option.setRequired(getSafeValue("required", row));
            option.setType(getSafeValue("type", row));
            option.setJavaType(getSafeValue("javaType", row));
            option.setEnums(getSafeValue("enum", row));
            option.setDeprecated(getSafeValue("deprecated", row));
            option.setDeprecationNote(getSafeValue("deprecationNote", row));
            option.setSecret(getSafeValue("secret", row));
            option.setDefaultValue(getSafeValue("defaultValue", row));
            option.setDescription(getSafeValue("description", row));
            // lets put required in the description
            if ("true".equals(option.getRequired())) {
                String desc = "*Required* " + option.getDescription();
                option.setDescription(desc);
            }
            // is the option deprecated then include that as well in the description
            if ("true".equals(option.getDeprecated())) {
                String desc = "*Deprecated* " + option.getDescription();
                option.setDescription(desc);
                if (!StringHelper.isEmpty(option.getDeprecationNote())) {
                    desc = option.getDescription();
                    if (!desc.endsWith(".")) {
                        desc = desc + ". Deprecation note: " + option.getDeprecationNote();
                    } else {
                        desc = desc + " Deprecation note: " + option.getDeprecationNote();
                    }
                    option.setDescription(desc);
                }
            }
            component.addComponentOption(option);

            // group separate between different options
            if (oldGroup == null || !oldGroup.equals(option.getGroup())) {
                option.setNewGroup(true);
            }
            oldGroup = option.getGroup();
        }

        oldGroup = null;
        rows = parseJsonSchema("properties", json, true);
        for (Map<String, String> row : rows) {
            EndpointOptionModel option = new EndpointOptionModel();
            option.setName(getSafeValue("name", row));
            option.setDisplayName(getSafeValue("displayName", row));
            option.setKind(getSafeValue("kind", row));
            option.setGroup(getSafeValue("group", row));
            option.setRequired(getSafeValue("required", row));
            option.setType(getSafeValue("type", row));
            option.setJavaType(getSafeValue("javaType", row));
            option.setEnums(getSafeValue("enum", row));
            option.setPrefix(getSafeValue("prefix", row));
            option.setMultiValue(getSafeValue("multiValue", row));
            option.setDeprecated(getSafeValue("deprecated", row));
            option.setDeprecationNote(getSafeValue("deprecationNote", row));
            option.setSecret(getSafeValue("secret", row));
            option.setDefaultValue(getSafeValue("defaultValue", row));
            option.setDescription(getSafeValue("description", row));
            // lets put required in the description
            if ("true".equals(option.getRequired())) {
                String desc = "*Required* " + option.getDescription();
                option.setDescription(desc);
            }
            // is the option deprecated then include that as well in the description
            if ("true".equals(option.getDeprecated())) {
                String desc = "*Deprecated* " + option.getDescription();
                option.setDescription(desc);
                if (!StringHelper.isEmpty(option.getDeprecationNote())) {
                    desc = option.getDescription();
                    if (!desc.endsWith(".")) {
                        desc = desc + ". Deprecation note: " + option.getDeprecationNote();
                    } else {
                        desc = desc + " Deprecation note: " + option.getDeprecationNote();
                    }
                    option.setDescription(desc);
                }
            }
            // separate the options in path vs parameter so we can generate two different tables
            if ("path".equals(option.getKind())) {
                component.addEndpointPathOption(option);
            } else {
                component.addEndpointOption(option);
            }

            // group separate between different options
            if (oldGroup == null || !oldGroup.equals(option.getGroup())) {
                option.setNewGroup(true);
            }
            oldGroup = option.getGroup();
        }

        return component;
    }

    private DataFormatModel generateDataFormatModel(String dataFormatName, String json) {
        List<Map<String, String>> rows = parseJsonSchema("dataformat", json, false);

        DataFormatModel dataFormat = new DataFormatModel();
        dataFormat.setTitle(getSafeValue("title", rows));
        dataFormat.setModelName(getSafeValue("modelName", rows));
        dataFormat.setName(getSafeValue("name", rows));
        dataFormat.setDescription(getSafeValue("description", rows));
        dataFormat.setFirstVersion(getSafeValue("firstVersion", rows));
        dataFormat.setLabel(getSafeValue("label", rows));
        dataFormat.setDeprecated(getSafeValue("deprecated", rows));
        dataFormat.setDeprecationNote(getSafeValue("deprecationNote", rows));
        dataFormat.setJavaType(getSafeValue("javaType", rows));
        dataFormat.setGroupId(getSafeValue("groupId", rows));
        dataFormat.setArtifactId(getSafeValue("artifactId", rows));
        dataFormat.setVersion(getSafeValue("version", rows));

        rows = parseJsonSchema("properties", json, true);
        for (Map<String, String> row : rows) {
            DataFormatOptionModel option = new DataFormatOptionModel();
            option.setName(getSafeValue("name", row));
            option.setDisplayName(getSafeValue("displayName", row));
            option.setKind(getSafeValue("kind", row));
            option.setType(getSafeValue("type", row));
            option.setJavaType(getSafeValue("javaType", row));
            option.setDeprecated(getSafeValue("deprecated", row));
            option.setDeprecationNote(getSafeValue("deprecationNote", row));
            option.setEnumValues(getSafeValue("enum", row));
            option.setDefaultValue(getSafeValue("defaultValue", row));
            option.setDescription(getSafeValue("description", row));

            // special for bindy as we reuse one readme file
            if (dataFormatName.startsWith("bindy") && option.getName().equals("type")) {
                option.setDefaultValue("");
                String doc = option.getDescription() + " The default value is either Csv or KeyValue depending on chosen dataformat.";
                option.setDescription(doc);
            }
            // lets put required in the description
            // is the option deprecated then include that as well in the description
            if ("true".equals(option.getDeprecated())) {
                String desc = "*Deprecated* " + option.getDescription();
                option.setDescription(desc);
                if (!StringHelper.isEmpty(option.getDeprecationNote())) {
                    desc = option.getDescription();
                    if (!desc.endsWith(".")) {
                        desc = desc + ". Deprecation note: " + option.getDeprecationNote();
                    } else {
                        desc = desc + " Deprecation note: " + option.getDeprecationNote();
                    }
                    option.setDescription(desc);
                }
            }

            // skip option named id
            if ("id".equals(option.getName())) {
                getLog().debug("Skipping option: " + option.getName());
            } else {
                dataFormat.addDataFormatOption(option);
            }
        }

        return dataFormat;
    }

    private LanguageModel generateLanguageModel(String languageName, String json) {
        List<Map<String, String>> rows = parseJsonSchema("language", json, false);

        LanguageModel language = new LanguageModel();
        language.setTitle(getSafeValue("title", rows));
        language.setModelName(getSafeValue("modelName", rows));
        language.setName(getSafeValue("name", rows));
        language.setDescription(getSafeValue("description", rows));
        language.setFirstVersion(getSafeValue("firstVersion", rows));
        language.setLabel(getSafeValue("label", rows));
        language.setDeprecated(getSafeValue("deprecated", rows));
        language.setDeprecationNote(getSafeValue("deprecationNote", rows));
        language.setJavaType(getSafeValue("javaType", rows));
        language.setGroupId(getSafeValue("groupId", rows));
        language.setArtifactId(getSafeValue("artifactId", rows));
        language.setVersion(getSafeValue("version", rows));

        rows = parseJsonSchema("properties", json, true);
        for (Map<String, String> row : rows) {
            LanguageOptionModel option = new LanguageOptionModel();
            option.setName(getSafeValue("name", row));
            option.setDisplayName(getSafeValue("displayName", row));
            option.setKind(getSafeValue("kind", row));
            option.setType(getSafeValue("type", row));
            option.setJavaType(getSafeValue("javaType", row));
            option.setDeprecated(getSafeValue("deprecated", row));
            option.setDeprecationNote(getSafeValue("deprecationNote", row));
            option.setEnumValues(getSafeValue("enum", row));
            option.setDefaultValue(getSafeValue("defaultValue", row));
            option.setDescription(getSafeValue("description", row));

            // is the option deprecated then include that as well in the description
            if ("true".equals(option.getDeprecated())) {
                String desc = "*Deprecated* " + option.getDescription();
                option.setDescription(desc);
                if (!StringHelper.isEmpty(option.getDeprecationNote())) {
                    desc = option.getDescription();
                    if (!desc.endsWith(".")) {
                        desc = desc + ". Deprecation note: " + option.getDeprecationNote();
                    } else {
                        desc = desc + " Deprecation note: " + option.getDeprecationNote();
                    }
                    option.setDescription(desc);
                }
            }

            // skip option named id/expression
            if ("id".equals(option.getName()) || "expression".equals(option.getName())) {
                getLog().debug("Skipping option: " + option.getName());
            } else {
                language.addLanguageOption(option);
            }
        }

        return language;
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

        rows = parseJsonSchema("properties", json, true);
        for (Map<String, String> row : rows) {
            EipOptionModel option = new EipOptionModel();
            option.setName(getSafeValue("name", row));
            option.setDisplayName(getSafeValue("displayName", row));
            option.setType(getSafeValue("type", row));
            option.setJavaType(getSafeValue("javaType", row));
            option.setRequired(getSafeValue("required", row));
            option.setDeprecated("true".equals(getSafeValue("deprecated", row)));
            option.setDeprecationNote(getSafeValue("deprecationNote", row));
            option.setDefaultValue(getSafeValue("defaultValue", row));
            option.setDescription(getSafeValue("description", row));
            option.setInput("true".equals(getSafeValue("input", row)));
            option.setOutput("true".equals(getSafeValue("output", row)));

            // lets put required in the description
            if ("true".equals(option.getRequired())) {
                String desc = "*Required* " + option.getDescription();
                option.setDescription(desc);
            }
            // is the option deprecated then include that as well in the description
            if (option.isDeprecated()) {
                String desc = "*Deprecated* " + option.getDescription();
                option.setDescription(desc);
                if (!StringHelper.isEmpty(option.getDeprecationNote())) {
                    desc = option.getDescription();
                    if (!desc.endsWith(".")) {
                        desc = desc + ". Deprecation note: " + option.getDeprecationNote();
                    } else {
                        desc = desc + " Deprecation note: " + option.getDeprecationNote();
                    }
                    option.setDescription(desc);
                }
            }

            // skip option named id/description/expression/outputs
            if ("id".equals(option.getName()) || "description".equals(option.getName())
                || "expression".equals(option.getName()) || "outputs".equals(option.getName())) {
                getLog().debug("Skipping option: " + option.getName());
            } else {
                eip.addEipOptionModel(option);
            }
        }

        return eip;
    }

    private String templateComponentHeader(ComponentModel model) throws MojoExecutionException {
        try {
            String template = loadText(UpdateReadmeMojo.class.getClassLoader().getResourceAsStream("component-header.mvel"));
            String out = (String) TemplateRuntime.eval(template, model);
            return out;
        } catch (Exception e) {
            throw new MojoExecutionException("Error processing mvel template. Reason: " + e, e);
        }
    }

    private String templateComponentOptions(ComponentModel model) throws MojoExecutionException {
        try {
            String template = loadText(UpdateReadmeMojo.class.getClassLoader().getResourceAsStream("component-options.mvel"));
            String out = (String) TemplateRuntime.eval(template, model);
            return out;
        } catch (Exception e) {
            throw new MojoExecutionException("Error processing mvel template. Reason: " + e, e);
        }
    }

    private String templateEndpointOptions(ComponentModel model) throws MojoExecutionException {
        try {
            String template = loadText(UpdateReadmeMojo.class.getClassLoader().getResourceAsStream("endpoint-options.mvel"));
            String out = (String) TemplateRuntime.eval(template, model);
            return out;
        } catch (Exception e) {
            throw new MojoExecutionException("Error processing mvel template. Reason: " + e, e);
        }
    }

    private String templateDataFormatOptions(DataFormatModel model) throws MojoExecutionException {
        try {
            String template = loadText(UpdateReadmeMojo.class.getClassLoader().getResourceAsStream("dataformat-options.mvel"));
            String out = (String) TemplateRuntime.eval(template, model);
            return out;
        } catch (Exception e) {
            throw new MojoExecutionException("Error processing mvel template. Reason: " + e, e);
        }
    }

    private String templateLanguageOptions(LanguageModel model) throws MojoExecutionException {
        try {
            String template = loadText(UpdateReadmeMojo.class.getClassLoader().getResourceAsStream("language-options.mvel"));
            String out = (String) TemplateRuntime.eval(template, model);
            return out;
        } catch (Exception e) {
            throw new MojoExecutionException("Error processing mvel template. Reason: " + e, e);
        }
    }

    private String templateEipOptions(EipModel model) throws MojoExecutionException {
        try {
            String template = loadText(UpdateReadmeMojo.class.getClassLoader().getResourceAsStream("eip-options.mvel"));
            String out = (String) TemplateRuntime.eval(template, model);
            return out;
        } catch (Exception e) {
            throw new MojoExecutionException("Error processing mvel template. Reason: " + e, e);
        }
    }

    private List<String> findComponentNames() {
        List<String> componentNames = new ArrayList<String>();
        for (Resource r : project.getBuild().getResources()) {
            File f = new File(r.getDirectory());
            if (!f.exists()) {
                f = new File(project.getBasedir(), r.getDirectory());
            }
            f = new File(f, "META-INF/services/org/apache/camel/component");

            if (f.exists() && f.isDirectory()) {
                File[] files = f.listFiles();
                if (files != null) {
                    for (File file : files) {
                        // skip directories as there may be a sub .resolver directory
                        if (file.isDirectory()) {
                            continue;
                        }
                        String name = file.getName();
                        if (name.charAt(0) != '.') {
                            componentNames.add(name);
                        }
                    }
                }
            }
        }
        return componentNames;
    }

    private List<String> findDataFormatNames() {
        List<String> dataFormatNames = new ArrayList<String>();
        for (Resource r : project.getBuild().getResources()) {
            File f = new File(r.getDirectory());
            if (!f.exists()) {
                f = new File(project.getBasedir(), r.getDirectory());
            }
            f = new File(f, "META-INF/services/org/apache/camel/dataformat");

            if (f.exists() && f.isDirectory()) {
                File[] files = f.listFiles();
                if (files != null) {
                    for (File file : files) {
                        // skip directories as there may be a sub .resolver directory
                        if (file.isDirectory()) {
                            continue;
                        }
                        String name = file.getName();
                        if (name.charAt(0) != '.') {
                            dataFormatNames.add(name);
                        }
                    }
                }
            }
        }
        return dataFormatNames;
    }
    private List<String> findLanguageNames() {
        List<String> languageNames = new ArrayList<String>();
        for (Resource r : project.getBuild().getResources()) {
            File f = new File(r.getDirectory());
            if (!f.exists()) {
                f = new File(project.getBasedir(), r.getDirectory());
            }
            f = new File(f, "META-INF/services/org/apache/camel/language");

            if (f.exists() && f.isDirectory()) {
                File[] files = f.listFiles();
                if (files != null) {
                    for (File file : files) {
                        // skip directories as there may be a sub .resolver directory
                        if (file.isDirectory()) {
                            continue;
                        }
                        String name = file.getName();
                        if (name.charAt(0) != '.') {
                            languageNames.add(name);
                        }
                    }
                }
            }
        }
        return languageNames;
    }

    private boolean isFailFast() {
        return failFast != null && failFast;
    }

}
