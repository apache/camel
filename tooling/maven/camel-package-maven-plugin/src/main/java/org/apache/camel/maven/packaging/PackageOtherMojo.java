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
import java.util.Properties;

import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.sonatype.plexus.build.incremental.BuildContext;

import static org.apache.camel.maven.packaging.StringHelper.camelDashToTitle;

/**
 * Analyses the Camel plugins in a project and generates extra descriptor information for easier auto-discovery in Camel.
 *
 * @goal generate-others-list
 */
public class PackageOtherMojo extends AbstractMojo {

    /**
     * The maven project.
     *
     * @parameter property="project"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /**
     * The output directory for generated components file
     *
     * @parameter default-value="${project.build.directory}/generated/camel/others"
     */
    protected File otherOutDir;

    /**
     * The output directory for generated languages file
     *
     * @parameter default-value="${project.build.directory}/classes"
     */
    protected File schemaOutDir;

    /**
     * Maven ProjectHelper.
     *
     * @component
     * @readonly
     */
    private MavenProjectHelper projectHelper;

    /**
     * build context to check changed files and mark them for refresh (used for
     * m2e compatibility)
     * 
     * @component
     * @readonly
     */
    private BuildContext buildContext;

    /**
     * Execute goal.
     *
     * @throws MojoExecutionException execution of the main class or one of the
     *                 threads it generated failed.
     * @throws MojoFailureException something bad happened...
     */
    public void execute() throws MojoExecutionException, MojoFailureException {
        prepareOthers(getLog(), project, projectHelper, otherOutDir, schemaOutDir, buildContext);
    }

    public static void prepareOthers(Log log, MavenProject project, MavenProjectHelper projectHelper, File otherOutDir,
                                     File schemaOutDir, BuildContext buildContext) throws MojoExecutionException {

        // are there any components, data formats or languages?
        for (Resource r : project.getBuild().getResources()) {
            File f = new File(r.getDirectory());
            if (!f.exists()) {
                f = new File(project.getBasedir(), r.getDirectory());
            }
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
        }

        // okay none of those then this is a other kind of artifact

        // first we need to setup the output directory because the next check
        // can stop the build before the end and eclipse always needs to know about that directory
        if (projectHelper != null) {
            projectHelper.addResource(project, otherOutDir.getPath(), Collections.singletonList("**/other.properties"), Collections.emptyList());
        }

        if (!PackageHelper.haveResourcesChanged(log, project, buildContext, "META-INF/services/org/apache/camel/component")
            && !PackageHelper.haveResourcesChanged(log, project, buildContext, "META-INF/services/org/apache/camel/dataformat")
            && !PackageHelper.haveResourcesChanged(log, project, buildContext, "META-INF/services/org/apache/camel/language")) {
            return;
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

            log.debug("Model " + otherModel);

            // write this to the directory
            File dir = schemaOutDir;
            dir.mkdirs();

            File out = new File(dir, name + ".json");
            OutputStream fos = buildContext.newFileOutputStream(out);
            String json = createJsonSchema(otherModel);
            fos.write(json.getBytes());
            fos.close();

            buildContext.refresh(out);

            log.debug("Generated " + out + " containing JSon schema for " + name + " other");
        } catch (Exception e) {
            throw new MojoExecutionException("Error loading other model. Reason: " + e, e);
        }

        // now create properties file
        File camelMetaDir = new File(otherOutDir, "META-INF/services/org/apache/camel/");

        Properties properties = new Properties();
        properties.put("name", name);
        properties.put("groupId", project.getGroupId());
        properties.put("artifactId", project.getArtifactId());
        properties.put("version", project.getVersion());
        properties.put("projectName", project.getName());
        if (project.getDescription() != null) {
            properties.put("projectDescription", project.getDescription());
        }

        camelMetaDir.mkdirs();
        File outFile = new File(camelMetaDir, "other.properties");

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
                    log.debug("No changes detected");
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

            log.info("Generated " + outFile);

        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write properties to " + outFile + ". Reason: " + e, e);
        }
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
