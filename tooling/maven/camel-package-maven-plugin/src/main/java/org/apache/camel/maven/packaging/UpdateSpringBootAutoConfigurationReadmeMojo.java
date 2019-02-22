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
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.apache.camel.maven.packaging.model.SpringBootAutoConfigureOptionModel;
import org.apache.camel.maven.packaging.model.SpringBootModel;
import org.apache.camel.util.json.DeserializationException;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.mvel2.templates.TemplateRuntime;
import org.sonatype.plexus.build.incremental.BuildContext;

import static org.apache.camel.maven.packaging.PackageHelper.loadText;
import static org.apache.camel.maven.packaging.PackageHelper.writeText;

/**
 * For all the Camel components that has Spring Boot starter JAR, their documentation
 * .adoc files in their component directory is updated to include spring boot auto configuration options.
 */
@Mojo(name = "update-spring-boot-auto-configuration-readme", threadSafe = true)
public class UpdateSpringBootAutoConfigurationReadmeMojo extends AbstractMojo {

    /**
     * The maven project.
     */
    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;

    /**
     * The project build directory
     *
     */
    @Parameter(defaultValue = "${project.build.directory}")
    protected File buildDir;

    /**
     * The documentation directory
     *
     */
    @Parameter(defaultValue = "${basedir}/../../../../components/")
    protected File componentsDir;

    /**
     * Whether to fail the build fast if any Warnings was detected.
     */
    @Parameter
    protected Boolean failFast;

    /**
     * Whether to fail if an option has no documentation.
     */
    @Parameter
    protected Boolean failOnMissingDescription;

    /**
     * build context to check changed files and mark them for refresh (used for
     * m2e compatibility)
     */
    @Component
    private BuildContext buildContext;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            executeStarter(project.getBasedir());
        } catch (Exception e) {
            throw new MojoFailureException("Error processing spring-configuration-metadata.json", e);
        }
    }

    private void executeStarter(File starter) throws Exception {
        File jsonFile = new File(buildDir, "classes/META-INF/spring-configuration-metadata.json");

        // only if there is components we should update the documentation files
        if (jsonFile.exists()) {
            getLog().debug("Processing Spring Boot auto-configuration file: " + jsonFile);
            Object js = Jsoner.deserialize(new FileReader(jsonFile));
            if (js != null) {
                String name = starter.getName();

                if (!isValidStarter(name)) {
                    return;
                }

                File compDir = getComponentsDir(name);

                File[] docFiles;
                File docFolder;
                String componentName;
                if ("camel-spring-boot".equals(name)) {
                    // special for camel-spring-boot where we also want to auto-generate the options in the adoc file
                    componentName = "spring-boot";
                    docFolder = new File(compDir, "/src/main/docs/");
                    docFiles = docFolder.listFiles(new ComponentDocFilter(componentName));
                } else if ("camel-univocity-parsers-starter".equals(name)) {
                    // special for univocity-parsers
                    componentName = "univocity";
                    docFolder = new File(compDir, "camel-univocity-parsers/src/main/docs/");
                    docFiles = docFolder.listFiles(new ComponentDocFilter(componentName));
                } else {
                    // skip camel-  and -starter in the end
                    componentName = name.substring(6, name.length() - 8);
                    getLog().debug("Camel component: " + componentName);
                    docFolder = new File(compDir, "camel-" + componentName + "/src/main/docs/");
                    docFiles = docFolder.listFiles(new ComponentDocFilter(componentName));

                    // maybe its one of those component that has subfolders with -api and -component
                    if (docFiles == null || docFiles.length == 0) {
                        docFolder = new File(compDir, "camel-" + componentName + "/camel-" + componentName + "-component/src/main/docs/");
                        docFiles = docFolder.listFiles(new ComponentDocFilter(componentName));
                    }
                }

                if (docFiles != null && docFiles.length > 0) {
                    List<File> files = Arrays.asList(docFiles);

                    // find out if the JAR has a Camel component, dataformat, or language
                    boolean hasComponentDataFormatOrLanguage = files.stream().anyMatch(
                        (f) -> f.getName().endsWith("-component.adoc") || f.getName().endsWith("-dataformat.adoc") || f.getName().endsWith("-language.adoc"));

                    // if so then skip the root adoc file as its just a introduction to the others
                    if (hasComponentDataFormatOrLanguage) {
                        files = Arrays.stream(docFiles).filter((f) -> !f.getName().equals(componentName + ".adoc")).collect(Collectors.toList());
                    }

                    if (files.size() == 1) {
                        List<SpringBootAutoConfigureOptionModel> models = parseSpringBootAutoConfigureModels(jsonFile, null);

                        // special for other kind of JARs that is not a regular Camel component,dataformat,language
                        boolean onlyOther = files.size() == 1 && !hasComponentDataFormatOrLanguage;
                        if (models.isEmpty() && onlyOther) {
                            // there are no spring-boot auto configuration for this other kind of JAR so lets just ignore this
                            return;
                        }
                        File docFile = files.get(0);

                        // check for missing description on options
                        boolean noDescription = false;
                        for (SpringBootAutoConfigureOptionModel o : models) {
                            if (StringHelper.isEmpty(o.getDescription())) {
                                noDescription = true;
                                getLog().warn("Option " + o.getName() + " has no description");
                            }
                        }
                        if (noDescription && isFailOnNoDescription()) {
                            throw new MojoExecutionException("Failed build due failOnMissingDescription=true");
                        }

                        String changed = templateAutoConfigurationOptions(models, componentName);
                        boolean updated = updateAutoConfigureOptions(docFile, changed);
                        if (updated) {
                            getLog().info("Updated doc file: " + docFile);
                        } else {
                            getLog().debug("No changes to doc file: " + docFile);
                        }
                    } else if (files.size() > 1) {
                        // when we have 2 or more files we need to filter the model options accordingly
                        for (File docFile : files) {
                            String docName = docFile.getName();
                            int pos = docName.lastIndexOf("-");
                            // spring-boot use lower cased keys
                            String prefix = pos > 0 ? docName.substring(0, pos).toLowerCase(Locale.US) : null;

                            List<SpringBootAutoConfigureOptionModel> models = parseSpringBootAutoConfigureModels(jsonFile, prefix);

                            // check for missing description on options
                            boolean noDescription = false;
                            for (SpringBootAutoConfigureOptionModel o : models) {
                                if (StringHelper.isEmpty(o.getDescription())) {
                                    noDescription = true;
                                    getLog().warn("Option " + o.getName() + " has no description");
                                }
                            }
                            if (noDescription && isFailOnNoDescription()) {
                                throw new MojoExecutionException("Failed build due failOnMissingDescription=true");
                            }

                            String changed = templateAutoConfigurationOptions(models, componentName);
                            boolean updated = updateAutoConfigureOptions(docFile, changed);
                            if (updated) {
                                getLog().info("Updated doc file: " + docFile);
                            } else {
                                getLog().debug("No changes to doc file: " + docFile);
                            }
                        }
                    }
                } else {
                    getLog().warn("No component docs found in folder: " + docFolder);
                    if (isFailFast()) {
                        throw new MojoExecutionException("Failed build due failFast=true");
                    }
                }
            }
        }
    }

    private File getComponentsDir(String name) {
        if ("camel-spring-boot".equals(name)) {
            // special for camel-spring-boot
            return project.getBasedir();
        } else {
            return componentsDir;
        }
    }

    private static final class ComponentDocFilter implements FileFilter {

        private final String componentName;

        public ComponentDocFilter(String componentName) {
            this.componentName = asComponentName(componentName);
        }

        @Override
        public boolean accept(File pathname) {
            String name = pathname.getName();
            return name.startsWith(componentName) && name.endsWith(".adoc");
        }
    }

    private static String asComponentName(String componentName) {
        if ("fastjson".equals(componentName)) {
            return "json-fastjson";
        } else if ("gson".equals(componentName)) {
            return "json-gson";
        } else if ("jackson".equals(componentName)) {
            return "json-jackson";
        } else if ("johnzon".equals(componentName)) {
            return "json-johnzon";
        } else if ("snakeyaml".equals(componentName)) {
            return "yaml-snakeyaml";
        } else if ("cassandraql".equals(componentName)) {
            return "cql";
        } else if ("josql".equals(componentName)) {
            return "sql";
        } else if ("juel".equals(componentName)) {
            return "el";
        } else if ("jsch".equals(componentName)) {
            return "scp";
        } else if ("printer".equals(componentName)) {
            return "lpr";
        } else if ("saxon".equals(componentName)) {
            return "xquery";
        } else if ("script".equals(componentName)) {
            return "javaScript";
        } else if ("stringtemplate".equals(componentName)) {
            return "string-template";
        } else if ("tagsoup".equals(componentName)) {
            return "tidyMarkup";
        }
        return componentName;
    }

    private static boolean isValidStarter(String name) {
        // skip these
        if ("camel-core-starter".equals(name)) {
            return false;
        }
        return true;
    }

    private List<SpringBootAutoConfigureOptionModel> parseSpringBootAutoConfigureModels(File file, String include) throws IOException, DeserializationException {
        getLog().debug("Parsing Spring Boot AutoConfigureModel using include: " + include);
        List<SpringBootAutoConfigureOptionModel> answer = new ArrayList<>();

        JsonObject obj = (JsonObject) Jsoner.deserialize(new FileReader(file));

        JsonArray arr = obj.getCollection("properties");
        if (arr != null && !arr.isEmpty()) {
            arr.forEach((e) -> {
                JsonObject row = (JsonObject) e;
                String name = row.getString("name");
                String javaType = row.getString("type");
                String desc = row.getStringOrDefault("description", "");
                String defaultValue = row.getStringOrDefault("defaultValue", "");

                // skip this special option and also if not matching the filter
                boolean skip = name.endsWith("customizer.enabled") || include != null && !name.contains("." + include + ".");
                if (!skip) {
                    SpringBootAutoConfigureOptionModel model = new SpringBootAutoConfigureOptionModel();
                    model.setName(name);
                    model.setJavaType(javaType);
                    model.setDefaultValue(defaultValue);
                    model.setDescription(desc);
                    answer.add(model);
                }
            });
        }

        return answer;
    }

    private boolean updateAutoConfigureOptions(File file, String changed) throws MojoExecutionException {
        if (!file.exists()) {
            return false;
        }

        try {
            String text = loadText(new FileInputStream(file));

            String existing = StringHelper.between(text, "// spring-boot-auto-configure options: START", "// spring-boot-auto-configure options: END");
            if (existing != null) {
                // remove leading line breaks etc
                existing = existing.trim();
                changed = changed.trim();
                if (existing.equals(changed)) {
                    return false;
                } else {
                    String before = StringHelper.before(text, "// spring-boot-auto-configure options: START");
                    String after = StringHelper.after(text, "// spring-boot-auto-configure options: END");
                    text = before + "// spring-boot-auto-configure options: START\n" + changed + "\n// spring-boot-auto-configure options: END" + after;
                    writeText(file, text);
                    return true;
                }
            } else {
                getLog().warn("Cannot find markers in file " + file);
                getLog().warn("Add the following markers");
                getLog().warn("\t// spring-boot-auto-configure options: START");
                getLog().warn("\t// spring-boot-auto-configure options: END");
                if (isFailFast()) {
                    throw new MojoExecutionException("Failed build due failFast=true");
                }
                return false;
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error reading file " + file + " Reason: " + e, e);
        }
    }

    private String templateAutoConfigurationOptions(List<SpringBootAutoConfigureOptionModel> options, String componentName) throws MojoExecutionException {
        SpringBootModel model = new SpringBootModel();
        model.setGroupId(project.getGroupId());
        model.setArtifactId("camel-" + componentName + "-starter");
        model.setVersion(project.getVersion());
        model.setOptions(options);

        try {
            String template = loadText(UpdateSpringBootAutoConfigurationReadmeMojo.class.getClassLoader().getResourceAsStream("spring-boot-auto-configure-options.mvel"));
            String out = (String) TemplateRuntime.eval(template, model);
            return out;
        } catch (Exception e) {
            throw new MojoExecutionException("Error processing mvel template. Reason: " + e, e);
        }
    }

    private boolean isFailFast() {
        return failFast != null && failFast;
    }

    private boolean isFailOnNoDescription() {
        return failOnMissingDescription != null && failOnMissingDescription;
    }

}
