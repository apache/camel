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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.camel.maven.packaging.model.ComponentModel;
import org.apache.camel.maven.packaging.model.ComponentOptionModel;
import org.apache.camel.maven.packaging.model.EndpointOptionModel;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.mvel2.templates.TemplateRuntime;
import org.sonatype.plexus.build.incremental.BuildContext;

import static org.apache.camel.maven.packaging.JSonSchemaHelper.getSafeValue;
import static org.apache.camel.maven.packaging.PackageHelper.loadText;
import static org.apache.camel.maven.packaging.PackageHelper.writeText;

/**
 * Generate or updates the component readme.md file in the project root directory.
 *
 * @goal update-readme
 */
public class ReadmeComponentMojo extends AbstractMojo {

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
     * build context to check changed files and mark them for refresh (used for
     * m2e compatibility)
     *
     * @component
     * @readonly
     */
    private BuildContext buildContext;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        // find the component names
        List<String> componentNames = findComponentNames();

        final Set<File> jsonFiles = new TreeSet<File>();
        PackageHelper.findJsonFiles(buildDir, jsonFiles, new PackageHelper.CamelComponentsModelFilter());

        // only if there is components we should update the documentation files
        if (!componentNames.isEmpty()) {
            getLog().info("Found " + componentNames.size() + " components");
            for (String componentName : componentNames) {
                String json = loadComponentJson(jsonFiles, componentName);
                if (json != null) {
                    File file = new File(docDir, componentName + ".adoc");
                    ComponentModel model = generateComponentModel(componentName, json);

                    boolean exists = file.exists();
                    boolean updated = false;
                    if (model.getComponentOptions() != null) {
                        String options = templateComponentOptions(model);
                        updated |= updateComponentOptions(file, options);
                    }
                    if (model.getEndpointOptions() != null) {
                        String options = templateEndpointOptions(model);
                        updated |= updateEndpointOptions(file, options);
                    }

                    if (updated) {
                        getLog().info("Updated doc file: " + file);
                    } else if (exists) {
                        getLog().info("No changes to doc file: " + file);
                    } else {
                        getLog().info("No component doc file: " + file);
                    }
                }
            }
        }
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
                    text = before + "\n// component options: START\n" + changed + "\n// component options: END\n" + after;
                    writeText(file, text);
                    return true;
                }
            } else {
                getLog().warn("Cannot find markers in file " + file);
                getLog().warn("Add the following markers");
                getLog().warn("\t// component options: START");
                getLog().warn("\t// component options: END");
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
                    text = before + "\n// endpoint options: START\n" + changed + "\n// endpoint options: END\n" + after;
                    writeText(file, text);
                    return true;
                }
            } else {
                getLog().warn("Cannot find markers in file " + file);
                getLog().warn("Add the following markers");
                getLog().warn("\t// endpoint options: START");
                getLog().warn("\t// endpoint options: END");
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

    private ComponentModel generateComponentModel(String componentName, String json) {
        List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("component", json, false);

        ComponentModel component = new ComponentModel();
        component.setScheme(JSonSchemaHelper.getSafeValue("scheme", rows));
        component.setSyntax(JSonSchemaHelper.getSafeValue("syntax", rows));
        component.setAlternativeSyntax(JSonSchemaHelper.getSafeValue("alternativeSyntax", rows));
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

        rows = JSonSchemaHelper.parseJsonSchema("componentProperties", json, true);
        for (Map<String, String> row : rows) {
            ComponentOptionModel option = new ComponentOptionModel();
            option.setName(getSafeValue("name", row));
            option.setKind(getSafeValue("kind", row));
            option.setType(getSafeValue("type", row));
            option.setJavaType(getSafeValue("javaType", row));
            option.setDeprecated(getSafeValue("deprecated", row));
            option.setDescription(getSafeValue("description", row));
            component.addComponentOption(option);
        }

        rows = JSonSchemaHelper.parseJsonSchema("properties", json, true);
        for (Map<String, String> row : rows) {
            EndpointOptionModel option = new EndpointOptionModel();
            option.setName(getSafeValue("name", row));
            option.setKind(getSafeValue("kind", row));
            option.setGroup(getSafeValue("group", row));
            option.setRequired(getSafeValue("required", row));
            option.setType(getSafeValue("type", row));
            option.setJavaType(getSafeValue("javaType", row));
            option.setEnums(getSafeValue("enum", row));
            option.setPrefix(getSafeValue("prefix", row));
            option.setMultiValue(getSafeValue("multiValue", row));
            option.setDeprecated(getSafeValue("deprecated", row));
            option.setDefaultValue(getSafeValue("defaultValue", row));
            option.setDescription(getSafeValue("description", row));
            // lets put required in the description
            if ("true".equals(option.getRequired())) {
                String desc = "*Required* " + option.getDescription();
                option.setDescription(desc);
            }
            component.addEndpointOption(option);
        }

        return component;
    }

    private String templateComponentHeader(ComponentModel model) throws MojoExecutionException {
        try {
            String template = loadText(ReadmeComponentMojo.class.getClassLoader().getResourceAsStream("component-header.mvel"));
            String out = (String) TemplateRuntime.eval(template, model);
            return out;
        } catch (Exception e) {
            throw new MojoExecutionException("Error processing mvel template. Reason: " + e, e);
        }
    }

    private String templateComponentOptions(ComponentModel model) throws MojoExecutionException {
        try {
            String template = loadText(ReadmeComponentMojo.class.getClassLoader().getResourceAsStream("component-options.mvel"));
            String out = (String) TemplateRuntime.eval(template, model);
            return out;
        } catch (Exception e) {
            throw new MojoExecutionException("Error processing mvel template. Reason: " + e, e);
        }
    }

    private String templateEndpointOptions(ComponentModel model) throws MojoExecutionException {
        try {
            String template = loadText(ReadmeComponentMojo.class.getClassLoader().getResourceAsStream("endpoint-options.mvel"));
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

}
