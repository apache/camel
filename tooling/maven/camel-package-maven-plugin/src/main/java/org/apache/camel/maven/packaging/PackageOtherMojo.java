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
import java.nio.file.Path;
import java.util.Collections;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.sonatype.plexus.build.incremental.BuildContext;

import static org.apache.camel.maven.packaging.StringHelper.camelDashToTitle;

/**
 * Analyses the Camel plugins in a project and generates extra descriptor information for easier auto-discovery in Camel.
 */
@Mojo(name = "generate-others-list", threadSafe = true)
public class PackageOtherMojo extends AbstractGeneratorMojo {

    /**
     * The output directory for generated components file
     */
    @Parameter(defaultValue = "${project.build.directory}/generated/camel/others")
    protected File otherOutDir;

    /**
     * The output directory for generated languages file
     */
    @Parameter(defaultValue = "${project.build.directory}/classes")
    protected File schemaOutDir;

    /**
     * Execute goal.
     *
     * @throws MojoExecutionException execution of the main class or one of the
     *                 threads it generated failed.
     * @throws MojoFailureException something bad happened...
     */
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

        prepareOthers(getLog(), project, projectHelper, otherOutDir, schemaOutDir, buildContext);
    }

    public static void prepareOthers(Log log, MavenProject project, MavenProjectHelper projectHelper, File otherOutDir,
                                     File schemaOutDir, BuildContext buildContext) throws MojoExecutionException {

        // first we need to setup the output directory because the next check
        // can stop the build before the end and eclipse always needs to know about that directory
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
            if (project.getName() != null && project.getName().contains("(deprecated)")) {
                otherModel.setDeprecated("true");
            } else {
                otherModel.setDeprecated("false");
            }
            otherModel.setFirstVersion(project.getProperties().getProperty("firstVersion"));
            otherModel.setLabel(project.getProperties().getProperty("label"));
            String title = project.getProperties().getProperty("title");
            if (title == null) {
                title = camelDashToTitle(name);
            }
            otherModel.setTitle(title);

            if (log.isDebugEnabled()) {
                log.debug("Model: " + otherModel);
            }

            String schema = createJsonSchema(otherModel);

            // write this to the directory
            Path out = schemaOutDir.toPath()
                    .resolve(name + ".json");
            updateResource(buildContext, out, schema);

            if (log.isDebugEnabled()) {
                log.debug("Generated " + out + " containing JSon schema for " + name + " other");
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error loading other model. Reason: " + e, e);
        }

        // now create properties file
        File camelMetaDir = new File(otherOutDir, "META-INF/services/org/apache/camel/");

        Path outFile = camelMetaDir.toPath().resolve("other.properties");
        String properties = createProperties(project, "name", name);
        updateResource(buildContext, outFile, properties);
        log.info("Generated " + outFile + " containing 1 Camel other: " + name);
    }

    private static String createJsonSchema(OtherModel otherModel) {
        StringBuilder buffer = new StringBuilder("{");
        // language model
        buffer.append("\n \"other\": {");
        buffer.append("\n    \"name\": \"").append(otherModel.getName()).append("\",");
        buffer.append("\n    \"kind\": \"").append("other").append("\",");
        if (otherModel.getTitle() != null) {
            buffer.append("\n    \"title\": \"").append(otherModel.getTitle()).append("\",");
        }
        if (otherModel.getDescription() != null) {
            buffer.append("\n    \"description\": \"").append(otherModel.getDescription()).append("\",");
        }
        buffer.append("\n    \"deprecated\": \"").append(otherModel.getDeprecated()).append("\",");
        if (otherModel.getFirstVersion() != null) {
            buffer.append("\n    \"firstVersion\": \"").append(otherModel.getFirstVersion()).append("\",");
        }
        if (otherModel.getLabel() != null) {
            buffer.append("\n    \"label\": \"").append(otherModel.getLabel()).append("\",");
        }
        buffer.append("\n    \"groupId\": \"").append(otherModel.getGroupId()).append("\",");
        buffer.append("\n    \"artifactId\": \"").append(otherModel.getArtifactId()).append("\",");
        buffer.append("\n    \"version\": \"").append(otherModel.getVersion()).append("\"");
        buffer.append("\n  }");
        buffer.append("\n}");
        return buffer.toString();
    }

    private static class OtherModel {
        private String name;
        private String title;
        private String description;
        private String deprecated;
        private String deprecationNote;
        private String firstVersion;
        private String label;
        private String groupId;
        private String artifactId;
        private String version;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getDeprecated() {
            return deprecated;
        }

        public void setDeprecated(String deprecated) {
            this.deprecated = deprecated;
        }

        public String getDeprecationNote() {
            return deprecationNote;
        }

        public void setDeprecationNote(String deprecationNote) {
            this.deprecationNote = deprecationNote;
        }

        public String getFirstVersion() {
            return firstVersion;
        }

        public void setFirstVersion(String firstVersion) {
            this.firstVersion = firstVersion;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getGroupId() {
            return groupId;
        }

        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }

        public String getArtifactId() {
            return artifactId;
        }

        public void setArtifactId(String artifactId) {
            this.artifactId = artifactId;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        @Override
        public String toString() {
            return "OtherModel["
                + "name='" + name + '\''
                + ", title='" + title + '\''
                + ", description='" + description + '\''
                + ", label='" + label + '\''
                + ", groupId='" + groupId + '\''
                + ", artifactId='" + artifactId + '\''
                + ", version='" + version + '\''
                + ']';
        }
    }

}
