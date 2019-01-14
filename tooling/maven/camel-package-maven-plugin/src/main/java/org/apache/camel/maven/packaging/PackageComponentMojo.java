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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.sonatype.plexus.build.incremental.BuildContext;

import static org.apache.camel.maven.packaging.PackageHelper.loadText;
import static org.apache.camel.maven.packaging.PackageHelper.writeText;

/**
 * Analyses the Camel plugins in a project and generates extra descriptor information for easier auto-discovery in Camel.
 */
@Mojo(name = "generate-components-list", threadSafe = true)
public class PackageComponentMojo extends AbstractMojo {

    /**
     * The maven project.
     */
    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;

    /**
     * The output directory for generated components file
     */
    @Parameter(defaultValue = "${project.build.directory}/generated/camel/components")
    protected File componentOutDir;

    /**
     * The project build directory
     *
     */
    @Parameter(defaultValue="${project.build.directory}")
    protected File buildDir;

    /**
     * Maven ProjectHelper.
     */
    @Component
    private MavenProjectHelper projectHelper;

    /**
     * build context to check changed files and mark them for refresh (used for
     * m2e compatibility)
     */
    @Component
    private BuildContext buildContext;

    /**
     * Execute goal.
     *
     * @throws MojoExecutionException execution of the main class or one of the
     *                 threads it generated failed.
     * @throws MojoFailureException something bad happened...
     */
    public void execute() throws MojoExecutionException, MojoFailureException {
        prepareComponent(getLog(), project, projectHelper, buildDir, componentOutDir, buildContext);
    }

    public static void prepareComponent(Log log, MavenProject project, MavenProjectHelper projectHelper, File buildDir, File componentOutDir, BuildContext buildContext) throws MojoExecutionException {

        File camelMetaDir = new File(componentOutDir, "META-INF/services/org/apache/camel/");

        // first we need to setup the output directory because the next check
        // can stop the build before the end and eclipse always needs to know about that directory 
        if (projectHelper != null) {
            projectHelper.addResource(project, componentOutDir.getPath(), Collections.singletonList("**/component.properties"), Collections.emptyList());
        }

        if (!PackageHelper.haveResourcesChanged(log, project, buildContext, "META-INF/services/org/apache/camel/component")) {
            return;
        }

        StringBuilder buffer = new StringBuilder();
        int count = 0;

        Map<String, String> components = new LinkedHashMap<>();

        File f = new File(project.getBasedir(), "target/classes");
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
                        count++;
                        if (buffer.length() > 0) {
                            buffer.append(" ");
                        }
                        buffer.append(name);
                    }

                    // grab the java class name for the discovered component
                    try {
                        Properties prop = new Properties();
                        prop.load(new FileInputStream(file));

                        String javaType = prop.getProperty("class");

                        components.put(name, javaType);
                        log.debug("Discovered component: " + name + " with class: " + javaType);

                    } catch (IOException e) {
                        throw new MojoExecutionException("Failed to load file " + file + ". Reason: " + e, e);
                    }
                }
            }
        }

        // we need to enrich the component json files with data we know have from this plugin
        enrichComponentJsonFiles(log, project, buildDir, components);

        if (count > 0) {
            Properties properties = new Properties();
            String names = buffer.toString();
            properties.put("components", names);
            properties.put("groupId", project.getGroupId());
            properties.put("artifactId", project.getArtifactId());
            properties.put("version", project.getVersion());
            properties.put("projectName", project.getName());
            if (project.getDescription() != null) {
                properties.put("projectDescription", project.getDescription());
            }

            camelMetaDir.mkdirs();
            File outFile = new File(camelMetaDir, "component.properties");

            // check if the existing file has the same content, and if so then leave it as is so we do not write any changes
            // which can cause a re-compile of all the source code
            if (outFile.exists()) {
                try {
                    Properties existing = new Properties();

                    InputStream is = new FileInputStream(outFile);
                    existing.load(is);
                    is.close();

                    // are the content the same?
                    if (existing.equals(properties)) {
                        log.debug("No component changes detected");
                        return;
                    }
                } catch (IOException e) {
                    // ignore
                }
            }

            try {
                OutputStream os = buildContext.newFileOutputStream(outFile);
                properties.store(os, "Generated by camel-package-maven-plugin");
                os.close();

                log.info("Generated " + outFile + " containing " + count + " Camel " + (count > 1 ? "components: " : "component: ") + names);

            } catch (IOException e) {
                throw new MojoExecutionException("Failed to write properties to " + outFile + ". Reason: " + e, e);
            }
        } else {
            log.debug("No META-INF/services/org/apache/camel/component directory found. Are you sure you have created a Camel component?");
        }
    }

    private static void enrichComponentJsonFiles(Log log, MavenProject project, File buildDir, Map<String, String> components) throws MojoExecutionException {
        final Set<File> files = PackageHelper.findJsonFiles(buildDir, p -> p.isDirectory() || p.getName().endsWith(".json"));

        for (File file : files) {
            // name without .json
            String shortName = file.getName().substring(0, file.getName().length() - 5);
            String javaType = components.getOrDefault(shortName, "");
            log.debug("Enriching file: " + file);

            try {
                String text = loadText(new FileInputStream(file));
                text = text.replace("@@@JAVATYPE@@@", javaType);
                text = text.replace("@@@DESCRIPTION@@@", project.getDescription());
                text = text.replace("@@@GROUPID@@@", project.getGroupId());
                text = text.replace("@@@ARTIFACTID@@@", project.getArtifactId());
                text = text.replace("@@@VERSIONID@@@", project.getVersion());
                writeText(file, text);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to update file " + file + ". Reason: " + e, e);
            }
        }
    }

}
