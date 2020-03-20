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

import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.model.OtherModel;
import org.apache.camel.tooling.util.PackageHelper;
import org.apache.camel.tooling.util.Strings;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * Analyses the Camel plugins in a project and generates extra descriptor
 * information for easier auto-discovery in Camel.
 */
@Mojo(name = "generate-others-list", threadSafe = true)
public class PackageOtherMojo extends AbstractGeneratorMojo {

    /**
     * The output directory for generated components file
     */
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources")
    protected File otherOutDir;

    /**
     * The output directory for generated languages file
     */
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources")
    protected File schemaOutDir;

    public PackageOtherMojo() {
    }

    public PackageOtherMojo(Log log, MavenProject project, MavenProjectHelper projectHelper, File otherOutDir,
                            File schemaOutDir, BuildContext buildContext) {
        setLog(log);
        this.project = project;
        this.projectHelper = projectHelper;
        this.otherOutDir = otherOutDir;
        this.schemaOutDir = schemaOutDir;
        this.buildContext = buildContext;
    }

    /**
     * Execute goal.
     *
     * @throws MojoExecutionException execution of the main class or one of the
     *             threads it generated failed.
     * @throws MojoFailureException something bad happened...
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        File f = new File(project.getBasedir(), "target/classes");
        File comp = new File(f, "META-INF/services/org/apache/camel/component");
        if (comp.exists() && comp.isDirectory()) {
            return;
        }
        File df = new File(f, "META-INF/services/org/apache/camel/dataformat");
        if (df.exists() && df.isDirectory()) {
            return;
        }
        File lan = new File(f, "META-INF/services/org/apache/camel/language");
        if (lan.exists() && lan.isDirectory()) {
            return;
        }

        prepareOthers();
    }

    public void prepareOthers() throws MojoExecutionException {
        Log log = getLog();

        // first we need to setup the output directory because the next check
        // can stop the build before the end and eclipse always needs to know
        // about that directory
        if (projectHelper != null) {
            projectHelper.addResource(project, otherOutDir.getPath(), Collections.singletonList("**/other.properties"), Collections.emptyList());
        }

        String name = project.getArtifactId();
        // strip leading camel-
        if (name.startsWith("camel-")) {
            name = name.substring(6);
        }

        try {
            // create json model
            OtherModel otherModel = new OtherModel();
            otherModel.setName(name);
            otherModel.setGroupId(project.getGroupId());
            otherModel.setArtifactId(project.getArtifactId());
            otherModel.setVersion(project.getVersion());
            otherModel.setDescription(project.getDescription());
            otherModel.setDeprecated(project.getName() != null && project.getName().contains("(deprecated)"));
            otherModel.setFirstVersion(project.getProperties().getProperty("firstVersion"));
            otherModel.setLabel(project.getProperties().getProperty("label"));
            String title = project.getProperties().getProperty("title");
            if (title == null) {
                title = Strings.camelDashToTitle(name);
            }
            otherModel.setTitle(title);

            if (log.isDebugEnabled()) {
                log.debug("Model: " + otherModel);
            }

            String schema = JsonMapper.createJsonSchema(otherModel);

            // write this to the directory
            String fileName = name + PackageHelper.JSON_SUFIX;
            updateResource(schemaOutDir.toPath(), fileName, schema);

            if (log.isDebugEnabled()) {
                log.debug("Generated " + fileName + " containing JSon schema for " + name + " other");
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error loading other model. Reason: " + e, e);
        }

        // now create properties file
        File camelMetaDir = new File(otherOutDir, "META-INF/services/org/apache/camel/");

        String properties = createProperties(project, "name", name);
        updateResource(camelMetaDir.toPath(), "other.properties", properties);
        log.info("Generated other.properties containing 1 Camel other: " + name);
    }

}
