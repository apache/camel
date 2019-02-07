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
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
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
public class PackageComponentMojo extends AbstractGeneratorMojo {

    /**
     * The output directory for generated components file
     */
    @Parameter(defaultValue = "${project.build.directory}/generated/camel/components")
    protected File componentOutDir;

    /**
     * The project build directory
     *
     */
    @Parameter(defaultValue = "${project.build.directory}")
    protected File buildDir;

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

    public static int prepareComponent(Log log, MavenProject project, MavenProjectHelper projectHelper, File buildDir, File componentOutDir, BuildContext buildContext) throws MojoExecutionException {

        File camelMetaDir = new File(componentOutDir, "META-INF/services/org/apache/camel/");

        // first we need to setup the output directory because the next check
        // can stop the build before the end and eclipse always needs to know about that directory 
        if (projectHelper != null) {
            projectHelper.addResource(project, componentOutDir.getPath(), Collections.singletonList("**/component.properties"), Collections.emptyList());
        }

        if (!PackageHelper.haveResourcesChanged(log, project, buildContext, "META-INF/services/org/apache/camel/component")) {
            return 0;
        }

        StringBuilder buffer = new StringBuilder();
        int count = 0;

        Set<String> components = new HashSet<>();
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
                        components.add(file.getName());
                    }
                }
            }
        }

        if (count > 0) {
            // we need to enrich the component json files with data we know have from this plugin
            enrichComponentJsonFiles(log, project, buildDir, components);
        }

        if (count > 0) {
            String names = buffer.toString();
            Path outFile = camelMetaDir.toPath().resolve("component.properties");
            String properties = createProperties(project, "components", names);
            updateResource(buildContext, outFile, properties);
            log.info("Generated " + outFile + " containing " + count + " Camel " + (count > 1 ? "components: " : "component: ") + names);
        } else {
            log.debug("No META-INF/services/org/apache/camel/component directory found. Are you sure you have created a Camel component?");
        }

        return count;
    }

    private static void enrichComponentJsonFiles(Log log, MavenProject project, File buildDir, Set<String> components) throws MojoExecutionException {
        final Set<File> files = PackageHelper.findJsonFiles(buildDir, p -> p.isDirectory() || p.getName().endsWith(".json"));

        for (File file : files) {
            // clip the .json suffix
            String name = file.getName().substring(0, file.getName().length() - 5);
            if (components.contains(name)) {
                log.debug("Enriching component: " + name);
                try {
                    String text = loadText(new FileInputStream(file));
                    text = text.replace("@@@DESCRIPTION@@@", project.getDescription());
                    text = text.replace("@@@GROUPID@@@", project.getGroupId());
                    text = text.replace("@@@ARTIFACTID@@@", project.getArtifactId());
                    text = text.replace("@@@VERSIONID@@@", project.getVersion());

                    // special for deprecated where you can quickly specify that in the pom.xml name
                    boolean deprecated = project.getName().contains("(deprecated)");
                    if (deprecated) {
                        // must start with 4 leading spaces as we want to replace the marker in the top of the file
                        text = text.replaceFirst(" {4}\"deprecated\": false,", "    \"deprecated\": true,");
                    }

                    writeText(file, text);
                } catch (IOException e) {
                    throw new MojoExecutionException("Failed to update file " + file + ". Reason: " + e, e);
                }
            }
        }
    }

}
