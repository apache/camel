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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
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
import java.util.stream.Collectors;

import org.apache.camel.tooling.model.AnnotationModel;
import org.apache.camel.tooling.model.ArtifactModel;
import org.apache.camel.tooling.model.BaseModel;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.DataFormatModel;
import org.apache.camel.tooling.model.EipModel;
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
import org.codehaus.plexus.build.BuildContext;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.ASTNode;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.AnnotationTypeMemberDeclaration;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.Javadoc;
import org.jboss.forge.roaster.model.source.AnnotationElementSource;
import org.jboss.forge.roaster.model.source.JavaAnnotationSource;
import org.mvel2.templates.TemplateRuntime;

import static org.apache.camel.tooling.util.PackageHelper.findCamelDirectory;

/**
 * Generate or updates the component/dataformat/language/eip documentation .adoc files in the project src/main/docs
 * directory.
 */
@Mojo(name = "update-readme", threadSafe = true)
public class UpdateReadmeMojo extends AbstractGeneratorMojo {

    //Set to true if you need to move a new manual attribute from text body to header attributes.
    private static final boolean RELOCATE_MANUAL_ATTRIBUTES = false;

    //Header attributes that are preserved through header generation
    private static final Pattern[] MANUAL_ATTRIBUTES = {
            Pattern.compile(":(group): *(.*)"),
            Pattern.compile(":(summary-group): *(.*)"),
            Pattern.compile(":(camel-spring-boot-name): *(.*)"),
            Pattern.compile(":(starter-artifactid): *(.*)")
    };

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
    @Parameter
    protected File eipDocDir;

    /**
     * Whether to fail the build fast if any Warnings was detected.
     */
    @Parameter
    protected Boolean failFast;

    protected List<Path> sourceRoots;

    @Override
    public void execute(MavenProject project, MavenProjectHelper projectHelper, BuildContext buildContext)
            throws MojoFailureException, MojoExecutionException {
        buildDir = new File(project.getBuild().getDirectory());
        componentDocDir = new File(project.getBasedir(), "src/main/docs");
        dataformatDocDir = new File(project.getBasedir(), "src/main/docs");
        languageDocDir = new File(project.getBasedir(), "/src/main/docs");
        languageDocDir2 = new File(project.getBasedir(), "/src/main/docs/modules/languages/pages");
        File engine = findCamelDirectory(project.getBasedir(), "camel-core-engine");
        eipDocDir = new File(engine, "/src/main/docs/modules/eips/pages");
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
            if (getLog().isDebugEnabled()) {
                getLog().debug("Found " + componentNames.size() + " components");
            }

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

                    boolean updated = updateHeader(componentName, file, model, " Component");

                    checkComponentHeader(file);
                    checkSince(file);

                    updated |= updateOptionsIn(file, "component-configure", "");
                    String options = evaluateTemplate("component-options.mvel", model);
                    updated |= updateOptionsIn(file, kind, options);

                    updated |= updateOptionsIn(file, "endpoint", "");

                    if (updated) {
                        getLog().info("Updated doc file: " + file);
                    } else if (exists) {
                        if (getLog().isDebugEnabled()) {
                            getLog().debug("No changes to doc file: " + file);
                        }
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

    private void executeOther() throws MojoExecutionException {
        final Set<File> jsonFiles = new TreeSet<>();
        PackageHelper.findJsonFiles(buildDir, jsonFiles);

        if (getLog().isDebugEnabled()) {
            getLog().debug("UpdateReadmeMojo jsonFiles: " + jsonFiles);
        }

        // only if there is components we should update the documentation files
        if (!jsonFiles.isEmpty()) {
            if (getLog().isDebugEnabled()) {
                getLog().debug("Found " + jsonFiles.size() + " miscellaneous components");
            }

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

                    boolean updated = updateHeader(componentName, file, model, " Component");
                    checkSince(file);

                    if (updated) {
                        getLog().info("Updated doc file: " + file);
                    } else if (exists) {
                        if (getLog().isDebugEnabled()) {
                            getLog().debug("No changes to doc file: " + file);
                        }
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
        final String kind = "dataformat";
        List<String> dataFormatNames = listDescriptorNamesOfType(kind);

        final Set<File> jsonFiles = new TreeSet<>();
        PackageHelper.findJsonFiles(buildDir, jsonFiles);

        // only if there is dataformat we should update the documentation files
        if (!dataFormatNames.isEmpty()) {
            if (getLog().isDebugEnabled()) {
                getLog().debug("Found " + dataFormatNames.size() + " dataformats");
            }

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
                    boolean updated = updateHeader(dataFormatName, file, model, " DataFormat");
                    checkSince(file);

                    String options = evaluateTemplate("dataformat-options.mvel", model);
                    updated |= updateOptionsIn(file, kind, options);

                    // optional annotation processings
                    // for now it's only applied to bindy but can be unlocked for other dataformats if needed
                    if ("bindy".equals(dataFormatName)) {
                        updated |= updateAnnotationsIn(file);
                    }

                    if (updated) {
                        getLog().info("Updated doc file: " + file);
                    } else if (exists) {
                        if (getLog().isDebugEnabled()) {
                            getLog().debug("No changes to doc file: " + file);
                        }
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
        if (isMailComponent(name)) {
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
            if (getLog().isDebugEnabled()) {
                getLog().debug("Found " + languageNames.size() + " languages");
            }

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

                    boolean updated = updateHeader(languageName, file, model, " Language");
                    checkSince(file);

                    String options = evaluateTemplate("language-options.mvel", model);
                    updated |= updateOptionsIn(file, kind, options);

                    if (updated) {
                        getLog().info("Updated doc file: " + file);
                    } else if (exists) {
                        if (getLog().isDebugEnabled()) {
                            getLog().debug("No changes to doc file: " + file);
                        }
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
        String currentDir = project.getBasedir().toString();
        if (!currentDir.endsWith("camel-core-engine")) {
            return;
        }

        final Set<File> jsonFiles = new TreeSet<>();

        // find all json files in camel-core
        File coreDir = findCamelDirectory(project.getBasedir(), "camel-core-model");

        if (coreDir.isDirectory()) {
            File target = new File(coreDir, "src/generated/resources/org/apache/camel/model");
            PackageHelper.findJsonFiles(target, jsonFiles);
        }

        // only if there is EIP we should update the documentation files
        if (!jsonFiles.isEmpty()) {
            if (getLog().isDebugEnabled()) {
                getLog().debug("Found " + jsonFiles.size() + " eips");
            }

            for (File jsonFile : jsonFiles) {
                String json = loadEipJson(jsonFile);
                if (json != null) {
                    EipModel model = JsonMapper.generateEipModel(json);

                    // we only want actual EIPs from the models
                    final String kind = "eip";
                    if (!model.getLabel().startsWith(kind)) {
                        continue;
                    }

                    String eipName = model.getName();

                    File file = new File(eipDocDir, eipName + "-" + kind + ".adoc");
                    boolean exists = file.exists();

                    boolean updated = updateHeader(eipName, file, model, " EIP");

                    String options = evaluateTemplate("eip-options.mvel", model);
                    updated |= updateOptionsIn(file, kind, options);

                    if (updated) {
                        getLog().info("Updated doc file: " + file);
                    } else if (exists) {
                        if (getLog().isDebugEnabled()) {
                            getLog().debug("No changes to doc file: " + file);
                        }
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
        if (isMailComponent(name)) {
            return "Mail";
        }

        return title;
    }

    private static boolean isMailComponent(String name) {
        return name.equals("imap") || name.equals("imaps") || name.equals("pop3") || name.equals("pop3s") || name.equals("smtp")
                || name.equals("smtps");
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
            String name, final File file, final BaseModel<? extends BaseOptionModel> model, String titleSuffix)
            throws MojoExecutionException {
        if (getLog().isDebugEnabled()) {
            getLog().debug("updateHeader " + file);
        }

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
                if (!RELOCATE_MANUAL_ATTRIBUTES && line.length() == 0) {
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

            //title
            String title = model.getTitle() + titleSuffix;
            if (model.isDeprecated()) {
                title += " (deprecated)";
            }
            newLines.add("= " + title);
            newLines.add(":doctitle: " + model.getTitle());
            String shortName = "mail".equals(name) ? "imap" : name;
            newLines.add(":shortname: " + shortName);

            if (model instanceof ArtifactModel<?>) {
                newLines.add(":artifactid: " + ((ArtifactModel<?>) model).getArtifactId());
            }
            newLines.add(":description: " + model.getDescription());
            newLines.add(":since: " + model.getFirstVersionShort());
            //TODO put the deprecation into the actual support level.
            newLines.add(":supportlevel: " + model.getSupportLevel().toString() + (model.isDeprecated() ? "-deprecated" : ""));
            if (model.isDeprecated()) {
                newLines.add(":deprecated: *deprecated*");
            }
            newLines.add(":tabs-sync-option:");
            if (model instanceof ComponentModel) {
                newLines.add(":component-header: " + generateComponentHeader((ComponentModel) model));
                if (Arrays.asList(model.getLabel().split(",")).contains("core")) {
                    newLines.add(":core:");
                }
            }

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
            if (updated || RELOCATE_MANUAL_ATTRIBUTES) {
                outer: for (String line : lines) {
                    if (!copy && line.trim().isEmpty()) {
                        copy = true;
                    } else if (copy) {

                        if (RELOCATE_MANUAL_ATTRIBUTES) {
                            for (Pattern attrName : MANUAL_ATTRIBUTES) {
                                Matcher m = attrName.matcher(line);
                                if (m.matches()) {
                                    updated = true;
                                    continue outer;
                                }
                            }
                        }

                        newLines.add(line);
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

    private void checkComponentHeader(final File file) throws MojoExecutionException {
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

    private void checkSince(final File file) throws MojoExecutionException {
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

    private boolean updateAnnotationsIn(final File file) throws MojoExecutionException {
        if (!file.exists()) {
            return false;
        }

        try {
            String text = PackageHelper.loadText(file);
            String updated = updateAnnotationRecursivelyIn(text);
            if (text.equals(updated)) {
                return false;
            }
            PackageHelper.writeText(file, updated);
            return true;
        } catch (IOException e) {
            throw new MojoExecutionException("Error reading file " + file + " Reason: " + e, e);
        }
    }

    private String updateAnnotationRecursivelyIn(String text) throws MojoExecutionException {
        String annotationInterface = Strings.between(text, "// annotation interface:", "// annotation options: START");
        if (annotationInterface == null) {
            return text;
        }
        annotationInterface = annotationInterface.trim();

        Class<?> annotation = loadClass(annotationInterface);
        if (!annotation.isAnnotation()) {
            throw new MojoExecutionException("Interface " + annotationInterface + " is not an annotation");
        }

        if (getLog().isDebugEnabled()) {
            getLog().debug("Processing annotation " + annotationInterface);
        }

        AnnotationModel model = generateAnnotationModel(annotation);
        String options = evaluateTemplate("annotation-options.mvel", model);
        String updated = options.trim();

        String existing = Strings.between(text, "// annotation options: START", "// annotation options: END");
        if (existing == null) {
            return text;
        }
        // remove leading line breaks etc
        existing = existing.trim();

        String after = Strings.after(text, "// annotation options: END");
        // process subsequent annotations
        String afterUpdated = updateAnnotationRecursivelyIn(after);

        if (existing.equals(updated) && Objects.equals(after, afterUpdated)) {
            return text;
        }

        String before = Strings.before(text, "// annotation options: START");
        return before + "// annotation options: START\n" + updated + "\n// annotation options: END" + afterUpdated;
    }

    private static String loadJsonFrom(Set<File> jsonFiles, String kind, String name) {
        for (File file : jsonFiles) {
            if (file.getName().equals(name + PackageHelper.JSON_SUFIX)) {
                String json = doLoad(file, kind);
                if (json != null) {
                    return json;
                }
            }
        }

        return null;
    }

    private static String loadJsonFrom(File file, String kind) {
        if (file.getName().endsWith(PackageHelper.JSON_SUFIX)) {
            String json = doLoad(file, kind);
            if (json != null) {
                return json;
            }
        }

        return null;
    }

    private static String doLoad(File file, String kind) {
        try {
            String json = PackageHelper.loadText(file);
            if (Objects.equals(kind, PackageHelper.getSchemaKind(json))) {
                return json;
            }
        } catch (IOException ignored) {
            // ignored
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
        return JsonMapper.generateComponentModel(json);
    }

    private OtherModel generateOtherModel(String json) {
        return JsonMapper.generateOtherModel(json);
    }

    private DataFormatModel generateDataFormatModel(String json) {
        return JsonMapper.generateDataFormatModel(json);
    }

    private AnnotationModel generateAnnotationModel(Class<?> annotation) {
        String source = loadJavaSource(annotation.getName());
        JavaAnnotationSource annotationSource = parseAnnotationSource(source);

        AnnotationModel model = new AnnotationModel();
        for (Method method : annotation.getDeclaredMethods()) {
            AnnotationModel.AnnotationOptionModel option = new AnnotationModel.AnnotationOptionModel();
            option.setName(method.getName());
            option.setType(method.getReturnType().getSimpleName());
            if (method.getDefaultValue() != null) {
                option.setOptional(true);
                option.setDefaultValue(method.getDefaultValue().toString());
            }

            String javadoc = findJavaDoc(source, annotationSource, method);
            if (!Strings.isNullOrEmpty(javadoc)) {
                option.setDescription(javadoc.trim());
            }

            model.addOption(option);
        }
        return model;
    }

    private String loadJavaSource(String className) {
        try {
            Path file = getSourceRoots().stream()
                    .map(d -> d.resolve(className.replace('.', '/') + ".java"))
                    .filter(Files::isRegularFile)
                    .findFirst()
                    .orElse(null);

            if (file == null) {
                throw new FileNotFoundException("Unable to find source for " + className);
            }
            return PackageHelper.loadText(file);
        } catch (IOException e) {
            String classpath;
            try {
                classpath = project.getCompileClasspathElements().toString();
            } catch (Exception e2) {
                classpath = e2.toString();
            }
            throw new RuntimeException(
                    "Unable to load source for class " + className + " in folders " + getSourceRoots()
                                       + " (classpath: " + classpath + ")");
        }
    }

    private JavaAnnotationSource parseAnnotationSource(String source) {
        return Roaster.parse(JavaAnnotationSource.class, source);
    }

    private List<Path> getSourceRoots() {
        if (sourceRoots == null) {
            sourceRoots = project.getCompileSourceRoots().stream()
                    .map(Paths::get)
                    .collect(Collectors.toList());
        }
        return sourceRoots;
    }

    private String findJavaDoc(String source, JavaAnnotationSource annotationSource, Method method) {
        AnnotationElementSource element = annotationSource.getAnnotationElement(method.getName());
        if (element == null) {
            return null;
        }
        return getJavaDocText(source, element);
    }

    static String getJavaDocText(String source, AnnotationElementSource member) {
        if (member == null) {
            return null;
        }
        AnnotationTypeMemberDeclaration decl = (AnnotationTypeMemberDeclaration) member.getInternal();
        Javadoc jd = decl.getJavadoc();
        if (source == null || jd.tags().isEmpty()) {
            return null;
        }
        ASTNode n = (ASTNode) jd.tags().get(0);
        String txt = source.substring(n.getStartPosition(), n.getStartPosition() + n.getLength());
        return txt
                .replaceAll(" *\n *\\* *\n", "\n\n")
                .replaceAll(" *\n *\\* +", "\n");
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
}
