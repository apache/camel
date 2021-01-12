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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.camel.tooling.model.ArtifactModel;
import org.apache.camel.tooling.model.BaseModel;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.DataFormatModel;
import org.apache.camel.tooling.model.EipModel;
import org.apache.camel.tooling.model.EipModel.EipOptionModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.model.LanguageModel;
import org.apache.camel.tooling.model.OtherModel;
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

import static org.apache.camel.tooling.util.PackageHelper.findCamelDirectory;

/**
 * Generate or updates the component/dataformat/language/eip readme.md and .adoc files in the project root directory.
 */
@Mojo(name = "update-readme", threadSafe = true)
public class UpdateReadmeMojo extends AbstractGeneratorMojo {

    //Header attributes that are preserved through header generation
    private static final Pattern[] MANUAL_ATTRIBUTES = {
            Pattern.compile(":(group): *(.*)"),
            Pattern.compile(":(summary-group): *(.*)") };

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
    @Parameter(defaultValue = "${project.basedir}/src/main/docs")
    protected File languageDocDir;

    /**
     * The other language documentation directory
     */
    @Parameter(defaultValue = "${project.basedir}/src/main/docs/modules/languages/pages")
    protected File languageDocDir2;

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
    public void execute(MavenProject project, MavenProjectHelper projectHelper, BuildContext buildContext)
            throws MojoFailureException, MojoExecutionException {
        buildDir = new File(project.getBuild().getDirectory());
        componentDocDir = new File(project.getBasedir(), "src/main/docs");
        dataformatDocDir = new File(project.getBasedir(), "src/main/docs");
        languageDocDir = new File(project.getBasedir(), "/src/main/docs");
        languageDocDir2 = new File(project.getBasedir(), "/src/main/docs/modules/languages/pages");
        eipDocDir = new File(project.getBasedir(), "src/main/docs/modules/eips/pages");
        super.execute(project, projectHelper, buildContext);
    }

    @Override
    public void execute() throws MojoExecutionException {
        getLog().debug("UpdateReadmeMojo execute");
        executeComponent();
        executeOther();
        executeDataFormat();
        executeLanguage();
        executeEips();
    }

    private void executeComponent() throws MojoExecutionException {
        // find the component names
        final String kind = "component";
        List<String> componentNames = listDescriptorNamesOfType(kind);

        final Set<File> jsonFiles = new TreeSet<>();
        PackageHelper.findJsonFiles(buildDir, jsonFiles);

        // only if there is components we should update the documentation files
        if (!componentNames.isEmpty()) {
            getLog().debug("Found " + componentNames.size() + " components");
            for (String componentName : componentNames) {
                String json = loadJsonFrom(jsonFiles, kind, componentName);
                if (json != null) {
                    // special for some components
                    componentName = asComponentName(componentName);

                    File file = new File(componentDocDir, componentName + "-" + kind + ".adoc");
                    boolean exists = file.exists();

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

                    boolean updated = updateHeader(componentName, file, model, " Component", kind);

                    checkComponentHeader(file, model);
                    checkSince(file, model);

                    // resolvePropertyPlaceholders is an option which only make
                    // sense to use if the component has other options
                    boolean hasOptions = model.getComponentOptions().stream()
                            .anyMatch(o -> !o.getName().equals("resolvePropertyPlaceholders"));
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
                    updated |= updateOptionsIn(file, kind, options);

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

                    if (updated || exists) {
                        try {
                            // if we run in camel-core project then add additional meta-data
                            File rootFile = findCamelDirectory(project.getBasedir(), "core/camel-core");
                            if (rootFile != null) {
                                Path root = rootFile.toPath().getParent().getParent();
                                String text = PackageHelper.loadText(file);
                                updateResource(
                                        root.resolve(
                                                "catalog/camel-catalog/src/generated/resources/org/apache/camel/catalog/docs"),
                                        file.getName(), text);
                                String rep = "$1\n"
                                             + "//THIS FILE IS COPIED: EDIT THE SOURCE FILE:\n"
                                             + ":page-source: " + root.relativize(file.toPath());
                                text = Pattern.compile("^(= .+)$", Pattern.MULTILINE).matcher(text).replaceAll(rep);
                                updateResource(root.resolve("docs/components/modules/ROOT/pages"), file.getName(), text);
                            }
                        } catch (IOException e) {
                            throw new MojoExecutionException("Error reading file " + file + " Reason: " + e, e);
                        }
                    }
                }
            }
        }
    }

    private void executeOther() throws MojoExecutionException {
        final Set<File> jsonFiles = new TreeSet<>();
        PackageHelper.findJsonFiles(buildDir, jsonFiles);
        getLog().debug("UpdateReadmeMojo jsonFiles: " + jsonFiles);

        // only if there is components we should update the documentation files
        if (!jsonFiles.isEmpty()) {
            getLog().debug("Found " + jsonFiles.size() + "miscellaneous components");
            for (File jsonFile : jsonFiles) {
                final String kind = "other";
                String json = loadJsonFrom(jsonFile, kind);
                if (json != null) {
                    // special for some components
                    OtherModel model = generateOtherModel(json);
                    String title = model.getTitle();
                    model.setTitle(title);

                    String componentName = asComponentName(model.getName());

                    File file = new File(componentDocDir, componentName + ".adoc");
                    boolean exists = file.exists();

                    // we only want the first scheme as the alternatives do not
                    boolean updated = updateHeader(componentName, file, model, " Component", kind);
                    checkSince(file, model);

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

                    if (updated || exists) {
                        try {
                            // if we run in camel-core project then add additional meta-data
                            File rootFile = findCamelDirectory(project.getBasedir(), "core/camel-core");
                            if (rootFile != null) {
                                Path root = rootFile.toPath().getParent().getParent();
                                String text = PackageHelper.loadText(file);
                                updateResource(
                                        root.resolve(
                                                "catalog/camel-catalog/src/generated/resources/org/apache/camel/catalog/docs"),
                                        file.getName(), text);
                                String rep = "$1\n"
                                             + "//THIS FILE IS COPIED: EDIT THE SOURCE FILE:\n"
                                             + ":page-source: " + root.relativize(file.toPath());
                                text = Pattern.compile("^(= .+)$", Pattern.MULTILINE).matcher(text).replaceAll(rep);
                                updateResource(root.resolve("docs/components/modules/others/pages"), file.getName(), text);
                            }
                        } catch (IOException e) {
                            throw new MojoExecutionException("Error reading file " + file + " Reason: " + e, e);
                        }
                    }
                }
            }
        }
    }

    private void executeDataFormat() throws MojoExecutionException {
        // find the dataformat names
        final String kind = "dataformat";
        List<String> dataFormatNames = listDescriptorNamesOfType(kind);

        final Set<File> jsonFiles = new TreeSet<>();
        PackageHelper.findJsonFiles(buildDir, jsonFiles);

        // only if there is dataformat we should update the documentation files
        if (!dataFormatNames.isEmpty()) {
            getLog().debug("Found " + dataFormatNames.size() + " dataformats");
            for (String dataFormatName : dataFormatNames) {
                String json = loadJsonFrom(jsonFiles, kind, dataFormatName);
                if (json != null) {
                    // special for some data formats
                    dataFormatName = asDataFormatName(dataFormatName);

                    File file = new File(dataformatDocDir, dataFormatName + "-" + kind + ".adoc");

                    DataFormatModel model = generateDataFormatModel(json);
                    // Bindy has 3 derived dataformats, but only one doc, so
                    // avoid any differences
                    // to make sure the build is stable
                    if ("bindy".equals(dataFormatName)) {
                        model.getOptions().stream().filter(o -> "type".equals(o.getName()))
                                .forEach(o -> o.setDefaultValue(null));
                    }

                    String title = asDataFormatTitle(model.getName(), model.getTitle());
                    model.setTitle(title);

                    boolean exists = file.exists();
                    boolean updated = updateHeader(dataFormatName, file, model, " DataFormat", kind);
                    checkSince(file, model);

                    String options = evaluateTemplate("dataformat-options.mvel", model);
                    updated |= updateOptionsIn(file, kind, options);

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

                    if (updated || exists) {
                        try {
                            // if we run in camel-core project then add additional meta-data
                            File rootFile = findCamelDirectory(project.getBasedir(), "core/camel-core");
                            if (rootFile != null) {
                                Path root = rootFile.toPath().getParent().getParent();
                                String text = PackageHelper.loadText(file);
                                updateResource(
                                        root.resolve(
                                                "catalog/camel-catalog/src/generated/resources/org/apache/camel/catalog/docs"),
                                        file.getName(), text);
                                String rep = "$1\n"
                                             + "//THIS FILE IS COPIED: EDIT THE SOURCE FILE:\n"
                                             + ":page-source: " + root.relativize(file.toPath());
                                text = Pattern.compile("^(= .+)$", Pattern.MULTILINE).matcher(text).replaceAll(rep);
                                updateResource(root.resolve("docs/components/modules/dataformats/pages"), file.getName(), text);
                            }
                        } catch (IOException e) {
                            throw new MojoExecutionException("Error reading file " + file + " Reason: " + e, e);
                        }
                    }
                }
            }
        }
    }

    private static String asComponentName(String name) {
        // special for some components which share the same readme file
        if (name.equals("imap") || name.equals("imaps") || name.equals("pop3") || name.equals("pop3s") || name.equals("smtp")
                || name.equals("smtps")) {
            return "mail";
        }

        return name;
    }

    private void executeLanguage() throws MojoExecutionException {
        // find the language names
        final String kind = "language";
        List<String> languageNames = listDescriptorNamesOfType(kind);

        final Set<File> jsonFiles = new TreeSet<>();
        PackageHelper.findJsonFiles(buildDir, jsonFiles);

        // only if there is language we should update the documentation files
        if (!languageNames.isEmpty()) {
            getLog().debug("Found " + languageNames.size() + " languages");
            for (String languageName : languageNames) {
                String json = loadJsonFrom(jsonFiles, kind, languageName);
                if (json != null) {
                    File file = new File(languageDocDir, languageName + "-" + kind + ".adoc");
                    boolean exists = file.exists();
                    if (!exists) {
                        file = new File(languageDocDir2, languageName + "-" + kind + ".adoc");
                        exists = file.exists();
                    }

                    LanguageModel model = JsonMapper.generateLanguageModel(json);
                    // skip option named id
                    model.getOptions().removeIf(
                            opt -> Objects.equals(opt.getName(), "id") || Objects.equals(opt.getName(), "expression"));
                    // enhanced for autowired
                    model.getOptions().stream().filter(BaseOptionModel::isAutowired).forEach(option -> {
                        option.setDescription("*Autowired* " + option.getDescription());
                    });
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

                    boolean updated = updateHeader(languageName, file, model, " Language", kind);
                    checkSince(file, model);

                    String options = evaluateTemplate("language-options.mvel", model);
                    updated |= updateOptionsIn(file, kind, options);

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

                    if (updated || exists) {
                        try {
                            // if we run in camel-core project then add additional meta-data
                            File rootFile = findCamelDirectory(project.getBasedir(), "core/camel-core");
                            if (rootFile != null) {
                                Path root = rootFile.toPath().getParent().getParent();
                                String text = PackageHelper.loadText(file);
                                updateResource(
                                        root.resolve(
                                                "catalog/camel-catalog/src/generated/resources/org/apache/camel/catalog/docs"),
                                        file.getName(), text);
                                String rep = "$1\n"
                                             + "//THIS FILE IS COPIED: EDIT THE SOURCE FILE:\n"
                                             + ":page-source: " + root.relativize(file.toPath());
                                text = Pattern.compile("^(= .+)$", Pattern.MULTILINE).matcher(text).replaceAll(rep);
                                updateResource(root.resolve("docs/components/modules/languages/pages"), file.getName(), text);
                            }
                        } catch (IOException e) {
                            throw new MojoExecutionException("Error reading file " + file + " Reason: " + e, e);
                        }
                    }
                }
            }
        }
    }

    private void executeEips() throws MojoExecutionException {
        // only run if in camel-core-model
        String currentDir = Paths.get(".").normalize().toAbsolutePath().toString();
        if (!currentDir.endsWith("camel-core-model")) {
            return;
        }

        final Set<File> jsonFiles = new TreeSet<>();

        // find all json files in camel-core
        File coreDir = new File(".");
        if (coreDir.isDirectory()) {
            File target = new File(coreDir, "target/classes/org/apache/camel/model");
            PackageHelper.findJsonFiles(target, jsonFiles);
        }

        // only if there is EIP we should update the documentation files
        if (!jsonFiles.isEmpty()) {
            getLog().debug("Found " + jsonFiles.size() + " eips");
            for (File jsonFile : jsonFiles) {
                String json = loadEipJson(jsonFile);
                if (json != null) {
                    EipModel model = JsonMapper.generateEipModel(json);
                    // skip option named id/description/expression/outputs
                    model.getOptions()
                            .removeIf(option -> "id".equals(option.getName()) || "description".equals(option.getName())
                                    || "expression".equals(option.getName())
                                    || "outputs".equals(option.getName()));
                    // lets put autowired in the description
                    model.getOptions().stream().filter(EipOptionModel::isAutowired).forEach(option -> {
                        String desc = "*Autowired* " + option.getDescription();
                        option.setDescription(desc);
                    });
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
                    final String kind = "eip";
                    if (!model.getLabel().startsWith(kind)) {
                        continue;
                    }

                    File file = new File(eipDocDir, eipName + "-" + kind + ".adoc");
                    boolean exists = file.exists();

                    boolean updated = updateHeader(eipName, file, model, " EIP", kind);

                    String options = evaluateTemplate("eip-options.mvel", model);
                    updated |= updateOptionsIn(file, kind, options);

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

                    if (updated || exists) {
                        try {
                            // if we run in camel-core project then add additional meta-data
                            File rootFile = findCamelDirectory(project.getBasedir(), "core/camel-core");
                            if (rootFile != null) {
                                Path root = rootFile.toPath().getParent().getParent();
                                String text = PackageHelper.loadText(file);
                                updateResource(
                                        root.resolve(
                                                "catalog/camel-catalog/src/generated/resources/org/apache/camel/catalog/docs"),
                                        file.getName(), text);
                            }
                        } catch (IOException e) {
                            throw new MojoExecutionException("Error reading file " + file + " Reason: " + e, e);
                        }
                    }
                }
            }
        }
    }

    private static String asComponentTitle(String name, String title) {
        // special for some components which share the same readme file
        if (name.equals("imap") || name.equals("imaps") || name.equals("pop3") || name.equals("pop3s") || name.equals("smtp")
                || name.equals("smtps")) {
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

    private boolean updateHeader(
            String name, final File file, final BaseModel<? extends BaseOptionModel> model, String titleSuffix,
            String kind)
            throws MojoExecutionException {
        getLog().debug("updateHeader " + file);
        final String linkSuffix = "-" + kind;
        if (model == null || !file.exists()) {
            return false;
        }

        boolean updated = false;
        try {
            String text = PackageHelper.loadText(file);

            String[] lines = text.split("\n");

            // check first if it is a standard documentation file, we expect at
            // least five lines
            if (lines.length < 5) {
                return false;
            }

            // find manual attributes
            Map<String, String> manualAttributes = new LinkedHashMap<>();
            for (String line : lines) {
                if (line.length() == 0) {
                    break;
                }
                for (Pattern attrName : MANUAL_ATTRIBUTES) {
                    Matcher m = attrName.matcher(line);
                    if (m.matches()) {
                        manualAttributes.put(m.group(1), m.group(2));
                        break;
                    }
                }
            }

            List<String> newLines = new ArrayList<>(lines.length + 8);

            //link
            newLines.add("[[" + name + linkSuffix + "]]");

            //title
            String title = model.getTitle() + titleSuffix;
            if (model.isDeprecated()) {
                title += " (deprecated)";
            }
            newLines.add("= " + title);
            newLines.add(":docTitle: " + model.getTitle());

            if (model instanceof ArtifactModel<?>) {
                newLines.add(":artifactId: " + ((ArtifactModel<?>) model).getArtifactId());
            }
            newLines.add(":description: " + model.getDescription());
            newLines.add(":since: " + model.getFirstVersionShort());
            //TODO put the deprecation into the actual support level.
            newLines.add(":supportLevel: " + model.getSupportLevel().toString() + (model.isDeprecated() ? "-deprecated" : ""));
            if (model.isDeprecated()) {
                newLines.add(":deprecated: *deprecated*");
            }
            if (model instanceof ComponentModel) {
                newLines.add(":component-header: " + generateComponentHeader((ComponentModel) model));
                if (Arrays.asList(model.getLabel().split(",")).contains("core")) {
                    newLines.add(":core:");
                }
            }

            newLines.add(
                    "include::{cq-version}@camel-quarkus:ROOT:partial$reference/" + kind + "s/" + name
                         + ".adoc[opts=optional]");

            if (!manualAttributes.isEmpty()) {
                newLines.add("//Manually maintained attributes");
                for (Map.Entry<String, String> entry : manualAttributes.entrySet()) {
                    newLines.add(":" + entry.getKey() + ": " + entry.getValue());
                }
            }

            newLines.add("");

            for (int i = 0; i < lines.length; i++) {
                if (i > newLines.size() - 1) {
                    break;
                }
                if (!newLines.get(i).equals(lines[i])) {
                    updated = true;
                    break;
                }
            }

            boolean copy = false;
            if (updated) {
                for (int i = 0; i < lines.length; i++) {
                    if (!copy && lines[i].isEmpty()) {
                        copy = true;
                    } else if (copy) {
                        newLines.add(lines[i]);
                    }
                }
                if (!copy) {
                    throw new MojoFailureException("File " + file + " has unexpected structure with no empty line.");
                }
            }

            if (updated) {
                // build the new updated text
                if (!newLines.get(newLines.size() - 1).isEmpty()) {
                    newLines.add("");
                }
                String newText = String.join("\n", newLines);
                PackageHelper.writeText(file, newText);
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error reading file " + file + " Reason: " + e, e);
        }

        return updated;
    }

    private void checkComponentHeader(final File file, final ComponentModel model) throws MojoExecutionException {
        if (!file.exists()) {
            return;
        }

        final String headerText = "*{component-header}*";
        String loadedText;

        try {
            loadedText = PackageHelper.loadText(file);

        } catch (Exception e) {
            throw new MojoExecutionException("Error reading file " + file + " Reason: " + e, e);
        }
        if (!loadedText.contains(headerText)) {
            throw new MojoExecutionException("File " + file + " does not contain required string `" + headerText + "'");
        }
    }

    private void checkSince(final File file, final ArtifactModel<?> model) throws MojoExecutionException {
        if (!file.exists()) {
            return;
        }

        final String sinceText = "*Since Camel {since}*";
        String loadedText;

        try {
            loadedText = PackageHelper.loadText(file);
        } catch (Exception e) {
            throw new MojoExecutionException("Error reading file " + file + " Reason: " + e, e);
        }
        if (!loadedText.contains(sinceText)) {
            throw new MojoExecutionException("File " + file + " does not contain required string '" + sinceText + "'");
        }
    }

    private static String generateComponentHeader(final ComponentModel model) {
        final boolean consumerOnly = model.isConsumerOnly();
        final boolean producerOnly = model.isProducerOnly();
        // if we have only producer support
        if (!consumerOnly && producerOnly) {
            return "Only producer is supported";
        }
        // if we have only consumer support
        if (consumerOnly && !producerOnly) {
            return "Only consumer is supported";
        }

        return "Both producer and consumer are supported";
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

    private static String loadJsonFrom(File file, String kind) {
        if (file.getName().endsWith(PackageHelper.JSON_SUFIX)) {
            try {
                String json = PackageHelper.loadText(file);
                if (Objects.equals(kind, PackageHelper.getSchemaKind(json))) {
                    return json;
                }
            } catch (IOException ignored) {
                // ignored
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
        Stream.concat(component.getComponentOptions().stream(), component.getEndpointOptions().stream())
                .filter(BaseOptionModel::isAutowired).forEach(option -> {
                    String desc = "*Autowired* " + option.getDescription();
                    option.setDescription(desc);
                });
        Stream.concat(component.getComponentOptions().stream(), component.getEndpointOptions().stream())
                .filter(BaseOptionModel::isRequired).forEach(option -> {
                    String desc = "*Required* " + option.getDescription();
                    option.setDescription(desc);
                });
        Stream.concat(component.getComponentOptions().stream(), component.getEndpointOptions().stream())
                .filter(BaseOptionModel::isDeprecated).forEach(option -> {
                    String desc = "*Deprecated* " + option.getDescription();
                    if (!Strings.isEmpty(option.getDeprecationNote())) {
                        if (!desc.endsWith(".")) {
                            desc += ".";
                        }
                        desc = desc + " Deprecation note: " + option.getDeprecationNote();
                    }
                    option.setDescription(desc);
                });
        Stream.concat(component.getComponentOptions().stream(), component.getEndpointOptions().stream())
                .filter(o -> o.getEnums() != null).forEach(option -> {
                    String desc = option.getDescription();
                    if (!desc.endsWith(".")) {
                        desc = desc + ".";
                    }
                    desc = desc + " There are " + option.getEnums().size() + " enums and the value can be one of: "
                           + wrapEnumValues(option.getEnums());
                    option.setDescription(desc);
                });
        return component;
    }

    private OtherModel generateOtherModel(String json) {
        OtherModel other = JsonMapper.generateOtherModel(json);
        return other;
    }

    private DataFormatModel generateDataFormatModel(String json) {
        DataFormatModel model = JsonMapper.generateDataFormatModel(json);
        // skip option named id
        model.getOptions().removeIf(opt -> Objects.equals(opt.getName(), "id"));
        model.getOptions().stream().filter(BaseOptionModel::isAutowired).forEach(option -> {
            String desc = "*Autowired* " + option.getDescription();
            option.setDescription(desc);
        });
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
            desc = desc + " There are " + option.getEnums().size() + " enums and the value can be one of: "
                   + wrapEnumValues(option.getEnums());
            option.setDescription(desc);
        });
        return model;
    }

    private static String evaluateTemplate(final String templateName, final Object model) throws MojoExecutionException {
        try (InputStream templateStream = UpdateReadmeMojo.class.getClassLoader().getResourceAsStream(templateName)) {
            String template = PackageHelper.loadText(templateStream);
            return (String) TemplateRuntime.eval(template, model, Collections.singletonMap("util", MvelHelper.INSTANCE));
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
