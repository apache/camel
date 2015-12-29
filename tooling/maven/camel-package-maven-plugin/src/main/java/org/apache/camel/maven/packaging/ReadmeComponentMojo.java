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
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
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
                    updateReadMeFile(readmeFile, componentName, json);
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

    private void updateReadMeFile(File readmeFile, String componentName, String json) throws MojoExecutionException {
        // TODO: use some template like velocity or freemarker

        List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("component", json, false);
        String scheme = getValue("scheme", rows);
        String syntax = getValue("syntax", rows);
        String title = getValue("title", rows);
        String description = getValue("description", rows);
        String label = getValue("label", rows);
        String groupId = getValue("groupId", rows);
        String artifactId = getValue("artifactId", rows);
        String version = getValue("version", rows);

        try {
            OutputStream os = buildContext.newFileOutputStream(readmeFile);
            os.write("##".getBytes());
            os.write(title.getBytes());
            os.write("\n\n".getBytes());
            os.write(description.getBytes());
            os.write("\n\n".getBytes());
            os.close();
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to update " + readmeFile + " file. Reason: " + e, e);
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
