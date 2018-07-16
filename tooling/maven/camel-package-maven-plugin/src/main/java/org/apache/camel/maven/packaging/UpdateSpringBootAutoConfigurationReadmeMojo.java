/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.maven.packaging;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.maven.packaging.model.SpringBootAutoConfigureOptionModel;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.json.simple.DeserializationException;
import org.json.simple.JsonArray;
import org.json.simple.JsonObject;
import org.json.simple.Jsoner;
import org.mvel2.templates.TemplateRuntime;
import org.sonatype.plexus.build.incremental.BuildContext;

import static org.apache.camel.maven.packaging.PackageHelper.loadText;
import static org.apache.camel.maven.packaging.PackageHelper.writeText;

/**
 * Generate or updates the component/dataformat/language/eip readme.md and .adoc files in the project root directory
 * to include spring boot auto configuration options.
 *
 * @goal update-spring-boot-auto-configuration-readme
 */
public class UpdateSpringBootAutoConfigurationReadmeMojo extends AbstractMojo {

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
     * @parameter default-value="${basedir}/../../../../components/"
     */
    protected File componentsDir;

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
            getLog().info("Processing file: " + jsonFile);
            Object js = Jsoner.deserialize(new FileReader(jsonFile));
            if (js != null) {
                String name = starter.getName();
                // skip camel-  and -starter in the end
                String componentName = name.substring(6, name.length() - 8);
                getLog().debug("Camel component: " + componentName);
                File docFolder = new File(componentsDir, "camel-" + componentName + "/src/main/docs/");
                // update all adoc files (as it may be component, language, data-format or just other kind)
                File[] docFiles = docFolder.listFiles((f) -> f.getName().startsWith(componentName) && f.getName().endsWith(".adoc"));
                if (docFiles != null && docFiles.length > 0) {
                    boolean onlyOther = docFiles.length == 1 && docFiles[0].getName().equals(componentName + ".adoc");
                    List models = parseSpringBootAutoConfigreModels(jsonFile);
                    if (models.isEmpty() && onlyOther) {
                        // there are no spring-boot auto configuration for this other kind of JAR so lets just ignore this
                        return;
                    }
                    String options = templateAutoConfigurationOptions(models);
                    for (File docFile : docFiles) {
                        boolean updated = updateAutoConfigureOptions(docFile, options);
                        if (updated) {
                            getLog().info("Updated doc file: " + docFile);
                        } else {
                            getLog().debug("No changes to doc file: " + docFile);
                        }
                        if (isFailFast()) {
                            throw new MojoExecutionException("Failed build due failFast=true");
                        }
                    }
                } else {
                    getLog().warn("No component docs found in folder: " + docFolder);
                }
            }
        }
    }

    private List parseSpringBootAutoConfigreModels(File file) throws IOException, DeserializationException {
        List<SpringBootAutoConfigureOptionModel> answer = new ArrayList<>();

        JsonObject obj = (JsonObject) Jsoner.deserialize(new FileReader(file));

        JsonArray arr = obj.getCollection("properties");
        if (arr != null && !arr.isEmpty()) {
            arr.forEach((e) -> {
                JsonObject row = (JsonObject) e;
                String name = row.getString("name");
                String javaType = row.getString("type");
                String desc = row.getString("description");
                String defaultValue = row.getString("defaultValue");

                // skip this special option
                boolean skip = name.endsWith("customizer.enabled");
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

    private String templateAutoConfigurationOptions(List<SpringBootAutoConfigureOptionModel> options) throws MojoExecutionException {
        try {
            String template = loadText(UpdateSpringBootAutoConfigurationReadmeMojo.class.getClassLoader().getResourceAsStream("spring-boot-auto-configure-options.mvel"));
            String out = (String) TemplateRuntime.eval(template, options);
            return out;
        } catch (Exception e) {
            throw new MojoExecutionException("Error processing mvel template. Reason: " + e, e);
        }
    }

    private boolean isFailFast() {
        return failFast != null && failFast;
    }

}
