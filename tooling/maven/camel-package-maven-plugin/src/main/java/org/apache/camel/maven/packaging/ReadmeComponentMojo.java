/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.maven.packaging;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.camel.maven.packaging.model.ComponentModel;
import org.apache.camel.maven.packaging.model.ComponentOptionModel;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.mvel2.templates.TemplateRuntime;
import org.sonatype.plexus.build.incremental.BuildContext;

import static org.apache.camel.maven.packaging.JSonSchemaHelper.getValue;
import static org.apache.camel.maven.packaging.PackageHelper.loadText;

/**
 * Generate or updates the component readme.md file in the project root directort.
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
     * The output directory for generated readme file
     *
     * @parameter default-value="${project.build.directory}"
     */
    protected File buildDir;

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

        // only if there is components we should create/update the readme file
        if (!componentNames.isEmpty()) {
            getLog().info("Found " + componentNames.size() + " components");
            File readmeFile = initReadMeFile();

            for (String componentName : componentNames) {
                String json = loadComponentJson(jsonFiles, componentName);
                if (json != null) {
                    ComponentModel model = generateComponentModel(componentName, json);
                    String header = templateComponentHeader(model);
                    String options = templateComponentOptions(model);
                    getLog().info(header);
                    getLog().info(options);
                }
            }
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
        component.setScheme(getValue("scheme", rows));
        component.setSyntax(getValue("syntax", rows));
        component.setTitle(getValue("title", rows));
        component.setDescription(getValue("description", rows));
        component.setLabel(getValue("label", rows));
        component.setDeprecated(getValue("deprecated", rows));
        component.setConsumerOnly(getValue("consumerOnly", rows));
        component.setProducerOnly(getValue("producerOnly", rows));
        component.setJavaType(getValue("javaType", rows));
        component.setGroupId(getValue("groupId", rows));
        component.setArtifactId(getValue("artifactId", rows));
        component.setVersion(getValue("version", rows));

        rows = JSonSchemaHelper.parseJsonSchema("componentProperties", json, true);

        List<ComponentOptionModel> options = new ArrayList<ComponentOptionModel>();
        for (Map<String, String> row : rows) {
            ComponentOptionModel option = new ComponentOptionModel();
            option.setKey(getValue("name", row));
            option.setKind(getValue("kind", row));
            option.setType(getValue("type", row));
            option.setJavaType(getValue("javaType", row));
            option.setDeprecated(getValue("javaType", row));
            option.setDescription(getValue("description", row));
            options.add(option);
        }
        component.setOptions(options);

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

    private File initReadMeFile() throws MojoExecutionException {
        File readmeDir = new File(buildDir, "..");
        File readmeFile = new File(readmeDir, "readme.md");

        // see if a file with name readme.md exists in any kind of case
        String[] names = readmeDir.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return "readme.md".equalsIgnoreCase(name);
            }
        });
        if (names != null && names.length == 1) {
            readmeFile = new File(readmeDir, names[0]);
        }

        boolean exists = readmeFile.exists();
        if (exists) {
            getLog().info("Using existing " + readmeFile.getName() + " file");
        } else {
            getLog().info("Creating new readme.md file");
        }

        return readmeFile;
    }

}
