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
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

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
@Mojo(name = "generate-components-list", threadSafe = true)
public class PackageComponentMojo extends AbstractGeneratorMojo {

    /**
     * The output directory for generated components file
     */
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources")
    protected File componentOutDir;

    /**
     * The project build directory
     */
    @Parameter(defaultValue = "${project.build.directory}")
    protected File buildDir;

    public PackageComponentMojo() {
    }

    public PackageComponentMojo(Log log, MavenProject project, MavenProjectHelper projectHelper,
                                File buildDir, File componentOutDir, BuildContext buildContext) {
        setLog(log);
        this.project = project;
        this.projectHelper = projectHelper;
        this.buildDir = buildDir;
        this.componentOutDir = componentOutDir;
        this.buildContext = buildContext;
    }

    /**
     * Execute goal.
     *
     * @throws MojoExecutionException execution of the main class or one of the threads it generated failed.
     * @throws MojoFailureException   something bad happened...
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        prepareComponent();
    }

    public int prepareComponent() {
        Log log = getLog();

        File camelMetaDir = new File(componentOutDir, "META-INF/services/org/apache/camel/");

        // first we need to setup the output directory because the next check
        // can stop the build before the end and eclipse always needs to know
        // about that directory
        if (projectHelper != null) {
            projectHelper.addResource(project, componentOutDir.getPath(), Collections.singletonList("**/component.properties"),
                    Collections.emptyList());
        }

        if (!haveResourcesChanged(log, project, buildContext, "META-INF/services/org/apache/camel/component")) {
            return 0;
        }

        Set<String> fileNames = new TreeSet<>();

        int count = 0;

        File f = componentOutDir;
        f = new File(f, "META-INF/services/org/apache/camel/component");
        if (f.exists() && f.isDirectory()) {
            File[] files = f.listFiles(file -> !file.isDirectory() && !file.isHidden());
            if (files != null) {
                for (File file : files) {
                    // skip directories as there may be a sub .resolver
                    // directory

                    count++;
                    fileNames.add(file.getName());

                }
            }
        }

        if (count > 0) {
            final String names = fileNames.stream().collect(Collectors.joining(" "));

            String properties = createProperties(project, "components", names);
            updateResource(camelMetaDir.toPath(), "component.properties", properties);
            log.info("Generated components containing " + count + " Camel "
                     + (count > 1 ? "components: " : "component: ") + names);
        } else {
            log.debug(
                    "No META-INF/services/org/apache/camel/component directory found. Are you sure you have created a Camel component?");
        }

        return count;
    }

}
