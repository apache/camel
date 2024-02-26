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
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.camel.maven.packaging.generics.ClassUtil;
import org.apache.camel.tooling.model.EipModel;
import org.apache.camel.tooling.model.EipModel.EipOptionModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.model.LanguageModel;
import org.apache.camel.tooling.model.LanguageModel.LanguageOptionModel;
import org.apache.camel.tooling.model.SupportLevel;
import org.apache.camel.tooling.util.PackageHelper;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.build.BuildContext;

/**
 * Analyses the Camel plugins in a project and generates extra descriptor information for easier auto-discovery in
 * Camel.
 */
@Mojo(name = "generate-languages-list", threadSafe = true)
public class PackageLanguageMojo extends AbstractGeneratorMojo {

    /**
     * The output directory for generated languages file
     */
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources")
    protected File languageOutDir;

    /**
     * The output directory for generated languages file
     */
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources")
    protected File schemaOutDir;

    /**
     * The project build directory
     */
    @Parameter(defaultValue = "${project.build.directory}")
    protected File buildDir;

    public PackageLanguageMojo() {
    }

    public PackageLanguageMojo(Log log, MavenProject project, MavenProjectHelper projectHelper,
                               File buildDir, File languageOutDir, File schemaOutDir,
                               BuildContext buildContext) {
        setLog(log);
        this.project = project;
        this.projectHelper = projectHelper;
        this.buildDir = buildDir;
        this.languageOutDir = languageOutDir;
        this.schemaOutDir = schemaOutDir;
        this.buildContext = buildContext;
    }

    /**
     * Execute goal.
     *
     * @throws org.apache.maven.plugin.MojoExecutionException execution of the main class or one of the threads it
     *                                                        generated failed.
     * @throws org.apache.maven.plugin.MojoFailureException   something bad happened...
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        prepareLanguage();
    }

    public int prepareLanguage() throws MojoExecutionException {
        Log log = getLog();

        File camelMetaDir = new File(languageOutDir, "META-INF/services/org/apache/camel/");

        // first we need to setup the output directory because the next check
        // can stop the build before the end and eclipse always needs to know
        // about that directory
        if (projectHelper != null) {
            projectHelper.addResource(project, languageOutDir.getPath(), Collections.singletonList("**/language.properties"),
                    Collections.emptyList());
        }

        if (!haveResourcesChanged(log, project, buildContext, "META-INF/services/org/apache/camel/language")) {
            return 0;
        }

        Map<String, String> javaTypes = new HashMap<>();

        StringBuilder buffer = new StringBuilder();
        int count = 0;

        File f = new File(project.getBasedir(), "target/classes");
        f = new File(f, "META-INF/services/org/apache/camel/language");
        if (f.exists() && f.isDirectory()) {
            File[] files = f.listFiles();
            if (files != null) {
                for (File file : files) {
                    String javaType = readClassFromCamelResource(file, buffer, buildContext);
                    if (!file.isDirectory() && file.getName().charAt(0) != '.') {
                        count++;
                    }
                    if (javaType != null) {
                        javaTypes.put(file.getName(), javaType);
                    }
                }
            }
        }

        // is this from Apache Camel then the data format is out of the box and
        // we should enrich the json schema with more details
        boolean apacheCamel = "org.apache.camel".equals(project.getGroupId());

        // find camel-core and grab the language model from there, and enrich
        // this model with information from this artifact
        // and create json schema model file for this language
        try {
            if (apacheCamel && count > 0) {
                File core = PackageHelper.findCamelCoreModelDirectory(project.getBasedir());
                if (core != null) {
                    for (Map.Entry<String, String> entry : javaTypes.entrySet()) {
                        String name = entry.getKey();
                        Class<?> javaType = loadClass(entry.getValue());
                        String modelName = asModelName(name);

                        String json = PackageHelper.loadText(new File(
                                core, "src/generated/resources/META-INF/org/apache/camel/model/language/" + modelName
                                      + PackageHelper.JSON_SUFIX));

                        LanguageModel languageModel = extractLanguageModel(project, json, name, javaType);
                        if (log.isDebugEnabled()) {
                            log.debug("Model: " + languageModel);
                        }

                        // build json schema for the data format
                        String schema = JsonMapper.createParameterJsonSchema(languageModel);
                        if (log.isDebugEnabled()) {
                            log.debug("JSON schema\n" + schema);
                        }

                        // write this to the directory
                        Path out = schemaOutDir.toPath().resolve(schemaSubDirectory(languageModel.getJavaType()))
                                .resolve(name + PackageHelper.JSON_SUFIX);
                        updateResource(schemaOutDir.toPath(),
                                schemaSubDirectory(languageModel.getJavaType()) + "/" + name + PackageHelper.JSON_SUFIX,
                                schema);

                        if (log.isDebugEnabled()) {
                            log.debug("Generated " + out + " containing JSON schema for " + name + " language");
                        }
                    }
                } else {
                    throw new MojoExecutionException(
                            "Error finding core/camel-core/target/camel-core-model-" + project.getVersion()
                                                     + ".jar file. Make sure camel-core has been built first.");
                }
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error loading language model from camel-core. Reason: " + e, e);
        }

        if (count > 0) {
            String names = Stream.of(buffer.toString().split(" ")).sorted().collect(Collectors.joining(" "));
            String properties = createProperties(project, "languages", names);
            updateResource(camelMetaDir.toPath(), "language.properties", properties);
            log.info("Generated language.properties containing " + count + " Camel "
                     + (count > 1 ? "languages: " : "language: ") + names);
        } else {
            log.debug(
                    "No META-INF/services/org/apache/camel/language directory found. Are you sure you have created a Camel language?");
        }

        return count;
    }

    protected static LanguageModel extractLanguageModel(MavenProject project, String json, String name, Class<?> javaType) {
        EipModel def = JsonMapper.generateEipModel(json);
        LanguageModel model = new LanguageModel();
        model.setName(name);
        model.setTitle(asTitle(name, def.getTitle()));
        model.setDescription(asDescription(name, def.getDescription()));
        model.setFirstVersion(def.getFirstVersion());
        model.setLabel(def.getLabel());
        model.setDeprecated(def.isDeprecated());
        model.setDeprecationNote(def.getDeprecationNote());
        model.setDeprecatedSince(project.getProperties().getProperty("deprecatedSince"));
        model.setJavaType(javaType.getCanonicalName());
        model.setModelName(def.getName());
        model.setModelJavaType(def.getJavaType());
        model.setGroupId(project.getGroupId());
        model.setArtifactId(project.getArtifactId());
        model.setVersion(project.getVersion());

        // grab level from annotation, pom.xml or default to stable
        String level = project.getProperties().getProperty("supportLevel");
        boolean experimental = ClassUtil.hasAnnotation("org.apache.camel.Experimental", javaType);
        if (experimental) {
            model.setSupportLevel(SupportLevel.Experimental);
        } else if (level != null) {
            model.setSupportLevel(SupportLevel.safeValueOf(level));
        } else {
            model.setSupportLevel(SupportLevelHelper.defaultSupportLevel(model.getFirstVersion(), model.getVersion()));
        }

        for (EipOptionModel opt : def.getOptions()) {
            LanguageOptionModel option = new LanguageOptionModel();
            option.setName(opt.getName());
            option.setKind(opt.getKind());
            option.setDisplayName(opt.getDisplayName());
            option.setGroup(opt.getGroup());
            option.setLabel(opt.getLabel());
            option.setRequired(opt.isRequired());
            option.setType(opt.getType());
            option.setJavaType(opt.getJavaType());
            option.setEnums(opt.getEnums());
            option.setOneOfs(opt.getOneOfs());
            option.setPrefix(opt.getPrefix());
            option.setOptionalPrefix(opt.getOptionalPrefix());
            option.setMultiValue(opt.isMultiValue());
            option.setDeprecated(opt.isDeprecated());
            option.setDeprecationNote(opt.getDeprecationNote());
            option.setSecret(opt.isSecret());
            option.setDefaultValue(opt.getDefaultValue());
            option.setDefaultValueNote(opt.getDefaultValueNote());
            option.setAsPredicate(opt.isAsPredicate());
            option.setConfigurationClass(opt.getConfigurationClass());
            option.setConfigurationField(opt.getConfigurationField());
            option.setDescription(opt.getDescription());
            model.addOption(option);
        }
        return model;
    }

    private static String readClassFromCamelResource(File file, StringBuilder buffer, BuildContext buildContext)
            throws MojoExecutionException {
        // skip directories as there may be a sub .resolver directory such as in
        // camel-script
        if (file.isDirectory()) {
            return null;
        }
        String name = file.getName();
        if (name.charAt(0) != '.') {
            if (!buffer.isEmpty()) {
                buffer.append(" ");
            }
            buffer.append(name);
        }

        if (!buildContext.hasDelta(file)) {
            // if this file has not changed,
            // then no need to store the javatype
            // for the json file to be generated again
            // (but we do need the name above!)
            return null;
        }

        // find out the javaType for each data format
        try {
            String text = PackageHelper.loadText(file);
            Map<String, String> map = PackageHelper.parseAsMap(text);
            return map.get("class");
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to read file " + file + ". Reason: " + e, e);
        }
    }

    private static String asModelName(String name) {
        // special for some languages
        if ("bean".equals(name)) {
            return "method";
        } else if ("file".equals(name)) {
            return "simple";
        }
        return name;
    }

    private static String asTitle(String name, String title) {
        // special for some languages
        if ("file".equals(name)) {
            return "File";
        }
        return title;
    }

    private static String asDescription(String name, String description) {
        // special for some languages
        if ("file".equals(name)) {
            return "File related capabilities for the Simple language";
        }
        return description;
    }

    private static String schemaSubDirectory(String javaType) {
        int idx = javaType.lastIndexOf('.');
        String pckName = javaType.substring(0, idx);
        return pckName.replace('.', '/');
    }

}
