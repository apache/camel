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
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.DataFormatModel;
import org.apache.camel.tooling.model.EipModel;
import org.apache.camel.tooling.model.EipModel.EipOptionModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.model.LanguageModel;
import org.apache.camel.tooling.util.PackageHelper;
import org.apache.camel.tooling.util.Strings;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.mvel2.templates.TemplateRuntime;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * Generate or updates the component/dataformat/language/eip readme.md and .adoc
 * files in the project root directory.
 */
@Mojo(name = "update-readme", threadSafe = true)
public class UpdateReadmeMojo extends AbstractGeneratorMojo {

    /**
     * The project build directory
     */
    @Parameter(defaultValue = "${project.build.directory}")
    protected File buildDir;

    /**
     * The component documentation directory
     */
    @Parameter(defaultValue = "${project.basedir}/src/main/docs")
    protected File componentDocDir;

    /**
     * The dataformat documentation directory
     */
    @Parameter(defaultValue = "${project.basedir}/src/main/docs")
    protected File dataformatDocDir;

    /**
     * The language documentation directory
     */
    @Parameter(defaultValue = "${project.basedir}/src/main/docs/modules/languages/pages")
    protected File languageDocDir;

    /**
     * The EIP documentation directory
     */
    @Parameter(defaultValue = "${project.basedir}/src/main/docs/modules/eips/pages")
    protected File eipDocDir;

    /**
     * Whether to fail the build fast if any Warnings was detected.
     */
    @Parameter
    protected Boolean failFast;

    @Override
    public void execute(MavenProject project, MavenProjectHelper projectHelper, BuildContext buildContext) throws MojoFailureException, MojoExecutionException {
        buildDir = new File(project.getBuild().getDirectory());
        componentDocDir = new File(project.getBasedir(), "src/main/docs");
        dataformatDocDir = new File(project.getBasedir(), "src/main/docs");
        languageDocDir = new File(project.getBasedir(), "/src/main/docs/modules/languages/pages");
        eipDocDir = new File(project.getBasedir(), "src/main/docs/modules/eips/pages");
        super.execute(project, projectHelper, buildContext);
    }

    @Override
    public void execute() throws MojoExecutionException {
        executeComponent();
        executeDataFormat();
        executeLanguage();
        executeEips();
    }

    private void executeComponent() throws MojoExecutionException {
        // find the component names
        List<String> componentNames = listDescriptorNamesOfType("component");

        final Set<File> jsonFiles = new TreeSet<>();
        PackageHelper.findJsonFiles(buildDir, jsonFiles);

        // only if there is components we should update the documentation files
        if (!componentNames.isEmpty()) {
            getLog().debug("Found " + componentNames.size() + " components");
            for (String componentName : componentNames) {
                String json = loadJsonFrom(jsonFiles, "component", componentName);
                if (json != null) {
                    // special for some components
                    componentName = asComponentName(componentName);

                    File file = new File(componentDocDir, componentName + "-component.adoc");

                    ComponentModel model = generateComponentModel(json);
                    String title = asComponentTitle(model.getScheme(), model.getTitle());
                    model.setTitle(title);

                    // we only want the first scheme as the alternatives do not
                    // have their own readme file
                    if (!Strings.isEmpty(model.getAlternativeSchemes())) {
                        String first = model.getAlternativeSchemes().split(",")[0];
                        if (!model.getScheme().equals(first)) {
                            continue;
                        }
                    }

                    String docTitle = model.getTitle() + " Component";
                    boolean deprecated = model.isDeprecated();
                    if (deprecated) {
                        docTitle += " (deprecated)";
                    }

                    boolean exists = file.exists();
                    boolean updated;
                    updated = updateLink(file, componentName + "-component");
                    updated |= updateTitles(file, docTitle);
                    updated |= updateAvailableFrom(file, model.getFirstVersion());
                    updated |= updateComponentHeader(file, model);

                    // resolvePropertyPlaceholders is an option which only make
                    // sense to use if the component has other options
                    boolean hasOptions = model.getComponentOptions().stream().anyMatch(o -> !o.getName().equals("resolvePropertyPlaceholders"));
                    if (!hasOptions) {
                        model.getComponentOptions().clear();
                    }

                    // Fix description in options
                    Stream.concat(model.getComponentOptions().stream(), model.getEndpointOptions().stream()).forEach(option -> {
                        String desc = option.getDescription();
                        desc = desc.replaceAll("\\\\n", "\n");
                        option.setDescription(desc);
                    });

                    String options = evaluateTemplate("component-options.mvel", model);
                    updated |= updateOptionsIn(file, "component", options);

                    options = evaluateTemplate("endpoint-options.mvel", model);
                    updated |= updateOptionsIn(file, "endpoint", options);

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

    private void executeDataFormat() throws MojoExecutionException {
        // find the dataformat names
        List<String> dataFormatNames = listDescriptorNamesOfType("dataformat");

        final Set<File> jsonFiles = new TreeSet<>();
        PackageHelper.findJsonFiles(buildDir, jsonFiles);

        // only if there is dataformat we should update the documentation files
        if (!dataFormatNames.isEmpty()) {
            getLog().debug("Found " + dataFormatNames.size() + " dataformats");
            for (String dataFormatName : dataFormatNames) {
                String json = loadJsonFrom(jsonFiles, "dataformat", dataFormatName);
                if (json != null) {
                    // special for some data formats
                    dataFormatName = asDataFormatName(dataFormatName);

                    File file = new File(dataformatDocDir, dataFormatName + "-dataformat.adoc");

                    DataFormatModel model = generateDataFormatModel(json);
                    // Bindy has 3 derived dataformats, but only one doc, so
                    // avoid any differences
                    // to make sure the build is stable
                    if ("bindy".equals(dataFormatName)) {
                        model.getOptions().stream().filter(o -> "type".equals(o.getName())).forEach(o -> o.setDefaultValue(null));
                    }

                    String title = asDataFormatTitle(model.getName(), model.getTitle());
                    model.setTitle(title);

                    String docTitle = model.getTitle() + " DataFormat";
                    boolean deprecated = model.isDeprecated();
                    if (deprecated) {
                        docTitle += " (deprecated)";
                    }

                    boolean exists = file.exists();
                    boolean updated;
                    updated = updateLink(file, dataFormatName + "-dataformat");
                    updated |= updateTitles(file, docTitle);
                    updated |= updateAvailableFrom(file, model.getFirstVersion());

                    String options = evaluateTemplate("dataformat-options.mvel", model);
                    updated |= updateOptionsIn(file, "dataformat", options);

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
        }

        return name;
    }

    private void executeLanguage() throws MojoExecutionException {
        // find the language names
        List<String> languageNames = listDescriptorNamesOfType("language");

        final Set<File> jsonFiles = new TreeSet<>();
        PackageHelper.findJsonFiles(buildDir, jsonFiles);

        // only if there is language we should update the documentation files
        if (!languageNames.isEmpty()) {
            getLog().debug("Found " + languageNames.size() + " languages");
            for (String languageName : languageNames) {
                String json = loadJsonFrom(jsonFiles, "language", languageName);
                if (json != null) {
                    File file = new File(languageDocDir, languageName + "-language.adoc");

                    LanguageModel model = JsonMapper.generateLanguageModel(json);
                    // skip option named id
                    model.getOptions().removeIf(opt -> Objects.equals(opt.getName(), "id") || Objects.equals(opt.getName(), "expression"));
                    // enhance description for deprecated options
                    model.getOptions().stream().filter(BaseOptionModel::isDeprecated).forEach(option -> {
                        String desc = "*Deprecated* " + option.getDescription();
                        if (!Strings.isEmpty(option.getDeprecationNote())) {
                            desc = option.getDescription();
                            if (!desc.endsWith(".")) {
                                desc = desc + ".";
                            }
                            desc += " Deprecation note: " + option.getDeprecationNote();
                        }
                        option.setDescription(desc);
                    });

                    String docTitle = model.getTitle() + " Language";
                    boolean deprecated = model.isDeprecated();
                    if (deprecated) {
                        docTitle += " (deprecated)";
                    }

                    boolean exists = file.exists();
                    boolean updated;
                    updated = updateLink(file, languageName + "-language");
                    updated |= updateTitles(file, docTitle);
                    updated |= updateAvailableFrom(file, model.getFirstVersion());

                    String options = evaluateTemplate("language-options.mvel", model);
                    updated |= updateOptionsIn(file, "language", options);

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

    private void executeEips() throws MojoExecutionException {
        // only run if in camel-core-engine
        String currentDir = Paths.get(".").normalize().toAbsolutePath().toString();
        if (!currentDir.endsWith("camel-core-engine")) {
            return;
        }

        final Set<File> jsonFiles = new TreeSet<>();

        // find all json files in camel-core
        File coreDir = new File(".");
        if (coreDir.isDirectory()) {
            File target = new File(coreDir, "target/classes/org/apache/camel/model");
            PackageHelper.findJsonFiles(target, jsonFiles);
        }

        // only if there is dataformat we should update the documentation files
        if (!jsonFiles.isEmpty()) {
            getLog().debug("Found " + jsonFiles.size() + " eips");
            for (File jsonFile : jsonFiles) {
                String json = loadEipJson(jsonFile);
                if (json != null) {
                    EipModel model = JsonMapper.generateEipModel(json);
                    // skip option named id/description/expression/outputs
                    model.getOptions().removeIf(option -> "id".equals(option.getName()) || "description".equals(option.getName()) || "expression".equals(option.getName())
                                                          || "outputs".equals(option.getName()));
                    // lets put required in the description
                    model.getOptions().stream().filter(EipOptionModel::isRequired).forEach(option -> {
                        String desc = "*Required* " + option.getDescription();
                        option.setDescription(desc);
                    });
                    // is the option deprecated then include that as well in the
                    // description
                    model.getOptions().stream().filter(EipOptionModel::isDeprecated).forEach(option -> {
                        String desc = "*Deprecated* " + option.getDescription();
                        if (!Strings.isEmpty(option.getDeprecationNote())) {
                            if (!desc.endsWith(".")) {
                                desc += ".";
                            }
                            desc = desc + " Deprecation note: " + option.getDeprecationNote();
                        }
                        option.setDescription(desc);
                    });

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
                    updated = updateLink(file, eipName + "-eip");
                    updated |= updateTitles(file, docTitle);

                    String options = evaluateTemplate("eip-options.mvel", model);
                    updated |= updateOptionsIn(file, "eip", options);

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
        }

        return title;
    }

    private static String asDataFormatName(String name) {
        // special for some dataformats which share the same readme file
        if (name.startsWith("bindy")) {
            return "bindy";
        }

        return name;
    }

    private static String asDataFormatTitle(String name, String title) {
        // special for some dataformats which share the same readme file
        if (name.startsWith("bindy")) {
            return "Bindy";
        }

        return title;
    }

    private static boolean updateLink(File file, String link) throws MojoExecutionException {
        if (!file.exists()) {
            return false;
        }

        boolean updated = false;

        try {
            List<String> newLines = new ArrayList<>();
            String text = PackageHelper.loadText(file);
            String[] lines = text.split("\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];

                if (i == 0) {
                    // first line is the link
                    String newLine = "[[" + link + "]]";
                    newLines.add(newLine);
                    updated = !line.equals(newLine);
                    if (updated) {
                        // its some old text so keep it
                        newLines.add(line);
                    }
                } else {
                    newLines.add(line);
                }
            }

            if (updated) {
                // build the new updated text
                String newText = newLines.stream().collect(Collectors.joining("\n"));
                PackageHelper.writeText(file, newText);
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error reading file " + file + " Reason: " + e, e);
        }

        return updated;
    }

    private static boolean updateTitles(File file, String title) throws MojoExecutionException {
        if (!file.exists()) {
            return false;
        }

        boolean updated = false;

        try {
            List<String> newLines = new ArrayList<>();

            String text = PackageHelper.loadText(file);
            String[] lines = text.split("\n");
            // line 0 is the link
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i];

                if (i == 1) {
                    // first line is the title to make the text less noisy we
                    // use level 2
                    String newLine = "= " + title;
                    newLines.add(newLine);
                    updated = !line.equals(newLine);
                    continue;
                }

                // use single line headers with # as level instead of the
                // cumbersome adoc weird style
                if (line.startsWith("^^^") || line.startsWith("~~~") || line.startsWith("+++")) {
                    String level = line.startsWith("+++") ? "===" : "==";

                    // transform legacy heading into new style
                    int idx = newLines.size() - 1;
                    String prev = newLines.get(idx);

                    newLines.set(idx, level + " " + prev);

                    // okay if 2nd-prev line is a [[title]] we need to remove
                    // that too
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
                PackageHelper.writeText(file, newText);
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error reading file " + file + " Reason: " + e, e);
        }

        return updated;
    }

    private boolean updateComponentHeader(final File file, final ComponentModel model) throws MojoExecutionException {
        if (!file.exists()) {
            return false;
        }

        final String markerStart = "// HEADER START";
        final String markerEnd = "// HEADER END";

        final String headerText = generateHeaderTextData(model);

        try {
            final String loadedText = PackageHelper.loadText(file);

            String existing = Strings.between(loadedText, markerStart, markerEnd);

            if (existing != null) {
                // remove leading line breaks etc
                existing = existing.trim();
                if (existing.equals(headerText)) {
                    return false;
                }

                final String before = Strings.before(loadedText, markerStart);
                final String after = Strings.after(loadedText, markerEnd);
                final String updatedHeaderText = before + markerStart + "\n" + headerText + "\n" + markerEnd + after;

                PackageHelper.writeText(file, updatedHeaderText);
                return true;
            } else {
                // so we don't have the marker, so we add it somewhere after the
                // camel version
                final String sinceVersion = "*Since Camel " + shortenVersion(model.getFirstVersion()) + "*";
                final String before = Strings.before(loadedText, sinceVersion);
                final String after = Strings.after(loadedText, sinceVersion);
                final String updatedHeaderText = before + sinceVersion + "\n\n" + markerStart + "\n" + headerText + "\n" + markerEnd + after;

                PackageHelper.writeText(file, updatedHeaderText);
                return true;
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error reading file " + file + " Reason: " + e, e);
        }
    }

    private static String generateHeaderTextData(final ComponentModel model) {
        final boolean consumerOnly = model.isConsumerOnly();
        final boolean producerOnly = model.isProducerOnly();
        // if we have only producer support
        if (!consumerOnly && producerOnly) {
            return "*Only producer is supported*";
        }
        // if we have only consumer support
        if (consumerOnly && !producerOnly) {
            return "*Only consumer is supported*";
        }

        return "*Both producer and consumer is supported*";
    }

    private static boolean updateAvailableFrom(final File file, final String firstVersion) throws MojoExecutionException {
        if (firstVersion == null || !file.exists()) {
            return false;
        }

        final String version = shortenVersion(firstVersion);

        boolean updated = false;

        try {
            String text = PackageHelper.loadText(file);

            String[] lines = text.split("\n");

            List<String> newLines = new ArrayList<>();

            // copy over to all new lines
            newLines.addAll(Arrays.asList(lines));

            // check first if it is a standard documentation file, we expect at
            // least five lines
            if (lines.length < 5) {
                return false;
            }

            // check the first four lines (ignoring the first line)
            boolean title = lines[1].startsWith("#") || lines[1].startsWith("=");
            boolean empty = lines[2].trim().isEmpty();
            boolean since = lines[3].trim().contains("Since Camel");
            boolean empty2 = lines[4].trim().isEmpty();

            if (title && empty && since) {
                String newLine = "*Since Camel " + version + "*";
                if (!newLine.equals(lines[3])) {
                    newLines.set(3, newLine);
                    updated = true;
                }
                if (!empty2) {
                    newLines.add(4, "");
                    updated = true;
                }
            } else if (!since) {
                String newLine = "*Since Camel " + version + "*";
                newLines.add(3, newLine);
                newLines.add(4, "");
                updated = true;
            }

            if (updated) {
                // build the new updated text
                String newText = String.join("\n", newLines);
                PackageHelper.writeText(file, newText);
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error reading file " + file + " Reason: " + e, e);
        }

        return updated;
    }

    private static String shortenVersion(final String firstVersion) {
        String version = firstVersion;
        // cut last digit so its not 2.18.0 but 2.18
        String[] parts = firstVersion.split("\\.");
        if (parts.length == 3 && parts[2].equals("0")) {
            version = parts[0] + "." + parts[1];
        }
        return version;
    }

    private boolean updateOptionsIn(final File file, final String kind, final String changed) throws MojoExecutionException {
        if (!file.exists()) {
            return false;
        }

        final String updated = changed.trim();
        try {
            String text = PackageHelper.loadText(file);

            String existing = Strings.between(text, "// " + kind + " options: START", "// " + kind + " options: END");
            if (existing != null) {
                // remove leading line breaks etc
                existing = existing.trim();
                if (existing.equals(updated)) {
                    return false;
                }

                String before = Strings.before(text, "// " + kind + " options: START");
                String after = Strings.after(text, "// " + kind + " options: END");
                text = before + "// " + kind + " options: START\n" + updated + "\n// " + kind + " options: END" + after;
                PackageHelper.writeText(file, text);
                return true;
            }

            getLog().warn("Cannot find markers in file " + file);
            getLog().warn("Add the following markers");
            getLog().warn("\t// " + kind + " options: START");
            getLog().warn("\t// " + kind + " options: END");
            if (isFailFast()) {
                throw new MojoExecutionException("Failed build due failFast=true");
            }
            return false;
        } catch (IOException e) {
            throw new MojoExecutionException("Error reading file " + file + " Reason: " + e, e);
        }
    }

    private static String loadJsonFrom(Set<File> jsonFiles, String kind, String name) {
        for (File file : jsonFiles) {
            if (file.getName().equals(name + PackageHelper.JSON_SUFIX)) {
                try {
                    String json = PackageHelper.loadText(file);
                    if (Objects.equals(kind, PackageHelper.getSchemaKind(json))) {
                        return json;
                    }
                } catch (IOException ignored) {
                    // ignored
                }
            }
        }

        return null;
    }

    private static String loadEipJson(File file) {
        try {
            String json = PackageHelper.loadText(file);
            if ("model".equals(PackageHelper.getSchemaKind(json))) {
                return json;
            }
        } catch (IOException ignored) {
            // ignore
        }
        return null;
    }

    private ComponentModel generateComponentModel(String json) {
        ComponentModel component = JsonMapper.generateComponentModel(json);
        Stream.concat(component.getComponentOptions().stream(), component.getEndpointOptions().stream()).filter(BaseOptionModel::isRequired).forEach(option -> {
            String desc = "*Required* " + option.getDescription();
            option.setDescription(desc);
        });
        Stream.concat(component.getComponentOptions().stream(), component.getEndpointOptions().stream()).filter(BaseOptionModel::isDeprecated).forEach(option -> {
            String desc = "*Deprecated* " + option.getDescription();
            if (!Strings.isEmpty(option.getDeprecationNote())) {
                if (!desc.endsWith(".")) {
                    desc += ".";
                }
                desc = desc + " Deprecation note: " + option.getDeprecationNote();
            }
            option.setDescription(desc);
        });
        Stream.concat(component.getComponentOptions().stream(), component.getEndpointOptions().stream()).filter(o -> o.getEnums() != null).forEach(option -> {
            String desc = option.getDescription();
            if (!desc.endsWith(".")) {
                desc = desc + ".";
            }
            desc = desc + " The value can be one of: " + wrapEnumValues(option.getEnums());
            option.setDescription(desc);
        });
        return component;
    }

    private DataFormatModel generateDataFormatModel(String json) {
        DataFormatModel model = JsonMapper.generateDataFormatModel(json);
        // skip option named id
        model.getOptions().removeIf(opt -> Objects.equals(opt.getName(), "id"));
        // enhance description for deprecated options
        model.getOptions().stream().filter(BaseOptionModel::isDeprecated).forEach(option -> {
            String desc = "*Deprecated* " + option.getDescription();
            if (!Strings.isEmpty(option.getDeprecationNote())) {
                desc = option.getDescription();
                if (!desc.endsWith(".")) {
                    desc = desc + ".";
                }
                desc += " Deprecation note: " + option.getDeprecationNote();
            }
            option.setDescription(desc);
        });
        model.getOptions().stream().filter(o -> o.getEnums() != null).forEach(option -> {
            String desc = option.getDescription();
            if (!desc.endsWith(".")) {
                desc = desc + ".";
            }
            desc = desc + " The value can be one of: " + wrapEnumValues(option.getEnums());
            option.setDescription(desc);
        });
        return model;
    }

    private static String evaluateTemplate(final String templateName, final Object model) throws MojoExecutionException {
        try (InputStream templateStream = UpdateReadmeMojo.class.getClassLoader().getResourceAsStream(templateName)) {
            String template = PackageHelper.loadText(templateStream);
            return (String)TemplateRuntime.eval(template, model, Collections.singletonMap("util", MvelHelper.INSTANCE));
        } catch (IOException e) {
            throw new MojoExecutionException("Error processing mvel template `" + templateName + "`", e);
        }
    }

    private List<String> listDescriptorNamesOfType(final String type) {
        List<String> names = new ArrayList<>();

        File f = new File(project.getBasedir(), "target/classes");
        f = new File(f, "META-INF/services/org/apache/camel/" + type);
        if (f.exists() && f.isDirectory()) {
            File[] files = f.listFiles();
            if (files != null) {
                for (File file : files) {
                    // skip directories as there may be a sub .resolver
                    // directory
                    if (file.isDirectory()) {
                        continue;
                    }
                    String name = file.getName();
                    if (name.charAt(0) != '.') {
                        names.add(name);
                    }
                }
            }
        }
        Collections.sort(names);
        return names;
    }

    private boolean isFailFast() {
        return failFast != null && failFast;
    }

    private String wrapEnumValues(List<String> enumValues) {
        // comma to space so we can wrap words (which uses space)
        return String.join(", ", enumValues);
    }

}
