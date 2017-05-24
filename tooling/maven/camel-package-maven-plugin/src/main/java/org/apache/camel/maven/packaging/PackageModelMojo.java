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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * Analyses the Camel EIPs in a project and generates extra descriptor information for easier auto-discovery in Camel.
 *
 * @goal generate-eips-list
 */
public class PackageModelMojo extends AbstractMojo {

    /**
     * The maven project.
     *
     * @parameter property="project"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /**
     * The camel-core directory
     *
     * @parameter default-value="${project.build.directory}"
     */
    protected File buildDir;

    /**
     * The output directory for generated models file
     *
     * @parameter default-value="${project.build.directory}/generated/camel/models"
     */
    protected File outDir;

    /**
     * Maven ProjectHelper.
     *
     * @component
     * @readonly
     */
    private MavenProjectHelper projectHelper;

    /**
     * build context to check changed files and mark them for refresh
     * (used for m2e compatibility)
     *
     * @component
     * @readonly
     */
    private BuildContext buildContext;

    /**
     * Execute goal.
     *
     * @throws org.apache.maven.plugin.MojoExecutionException execution of the main class or one of the
     *                 threads it generated failed.
     * @throws org.apache.maven.plugin.MojoFailureException something bad happened...
     */
    public void execute() throws MojoExecutionException, MojoFailureException {
        prepareModel(getLog(), project, projectHelper, outDir, buildDir, buildContext);
    }

    public static void prepareModel(Log log, MavenProject project, MavenProjectHelper projectHelper, File modelOutDir,
                                    File buildDir, BuildContext buildContext) throws MojoExecutionException {

        File camelMetaDir = new File(modelOutDir, "META-INF/services/org/apache/camel/");
        camelMetaDir.mkdirs();

        Set<File> jsonFiles = new TreeSet<File>();

        // find all json files in camel-core
        if (buildDir != null && buildDir.isDirectory()) {
            File target = new File(buildDir, "classes/org/apache/camel/model");
            PackageHelper.findJsonFiles(target, jsonFiles, new PackageHelper.CamelComponentsModelFilter());
        }

        List<String> models = new ArrayList<String>();
        // sort the names
        for (File file : jsonFiles) {
            String name = file.getName();
            if (name.endsWith(".json")) {
                // strip out .json from the name
                String modelName = name.substring(0, name.length() - 5);
                models.add(modelName);
            }
        }
        Collections.sort(models);

        File outFile = new File(camelMetaDir, "model.properties");

        // check if the existing file has the same content, and if so then leave it as is so we do not write any changes
        // which can cause a re-compile of all the source code
        if (outFile.exists()) {
            try {
                List<String> existing = FileUtils.readLines(outFile);
                // skip comment lines
                existing = existing.stream().filter(l -> !l.startsWith("#")).collect(Collectors.toList());

                // are the content the same?
                if (models.containsAll(existing)) {
                    log.debug("No model changes detected");
                    return;
                }
            } catch (IOException e) {
                // ignore
            }
        }

        try {
            OutputStream os = buildContext.newFileOutputStream(outFile);
            FileOutputStream fos = new FileOutputStream(outFile, false);
            fos.write("#Generated by camel-package-maven-plugin\n".getBytes());
            for (String name : models) {
                fos.write(name.getBytes());
                fos.write("\n".getBytes());
            }
            fos.close();
            os.close();

            log.info("Generated " + outFile + " containing " + models.size() + " Camel models");

        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write properties to " + outFile + ". Reason: " + e, e);
        }

    }

}
