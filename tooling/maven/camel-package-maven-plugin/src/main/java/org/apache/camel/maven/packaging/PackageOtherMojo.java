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
import org.apache.camel.tooling.model.SupportLevel;
import org.apache.camel.tooling.util.PackageHelper;
import org.apache.camel.tooling.util.Strings;
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
@Mojo(name = "generate-others-list", threadSafe = true)
public class PackageOtherMojo extends AbstractGeneratorMojo {

    @Parameter(defaultValue = "${project.basedir}/src/generated/resources")
    protected File otherOutDir;

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
     * @throws MojoExecutionException execution of the main class or one of the threads it generated failed.
     * @throws MojoFailureException   something bad happened...
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        // should be JAR packaging
        if ("pom".equals(project.getPackaging())) {
            return;
        }

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
            projectHelper.addResource(project, otherOutDir.getPath(), Collections.singletonList("**/other.properties"),
                    Collections.emptyList());
        }

        String name = project.getArtifactId();
        // strip leading camel-
        if (name.startsWith("camel-")) {
            name = name.substring(6);
        }

        try {
            // create json model
            OtherModel model = new OtherModel();
            model.setName(name);
            model.setGroupId(project.getGroupId());
            model.setArtifactId(project.getArtifactId());
            model.setVersion(project.getVersion());
            model.setDescription(project.getDescription());
            model.setDeprecated(project.getName() != null && project.getName().contains("(deprecated)"));
            model.setDeprecatedSince(project.getProperties().getProperty("deprecatedSince"));
            model.setFirstVersion(project.getProperties().getProperty("firstVersion"));
            model.setLabel(project.getProperties().getProperty("label"));
            String title = project.getProperties().getProperty("title");
            if (title == null) {
                title = Strings.camelDashToTitle(name);
            }
            model.setTitle(title);

            // grab level from pom.xml or default to stable
            String level = project.getProperties().getProperty("supportLevel");
            if (level != null) {
                model.setSupportLevel(SupportLevel.safeValueOf(level));
            } else {
                model.setSupportLevel(SupportLevelHelper.defaultSupportLevel(model.getFirstVersion(), model.getVersion()));
            }

            if (log.isDebugEnabled()) {
                log.debug("Model: " + model);
            }

            String schema = JsonMapper.createJsonSchema(model);

            // write this to the directory
            String fileName = name + PackageHelper.JSON_SUFIX;
            updateResource(schemaOutDir.toPath(), fileName, schema);

            if (log.isDebugEnabled()) {
                log.debug("Generated " + fileName + " containing JSON schema for " + name + " other");
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
