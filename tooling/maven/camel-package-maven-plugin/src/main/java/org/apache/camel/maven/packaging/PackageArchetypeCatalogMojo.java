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
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.tooling.util.PackageHelper;
import org.apache.camel.tooling.util.Strings;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

/**
 * Creates the Maven catalog for the Camel archetypes
 */
@Mojo(name = "generate-and-attach-archetype-catalog", threadSafe = true)
public class PackageArchetypeCatalogMojo extends AbstractMojo {

    /**
     * The maven project.
     */
    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;

    /**
     * The output directory for generated archetypes
     */
    @Parameter(defaultValue = "${project.build.directory}/classes/")
    protected File outDir;

    /**
     * Maven ProjectHelper.
     */
    @Component
    private MavenProjectHelper projectHelper;

    /**
     * Execute goal.
     *
     * @throws org.apache.maven.plugin.MojoExecutionException execution of the
     *             main class or one of the threads it generated failed.
     * @throws org.apache.maven.plugin.MojoFailureException something bad
     *             happened...
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        // only generate this for the root pom
        if ("pom".equals(project.getModel().getPackaging())) {
            try {
                generateArchetypeCatalog(getLog(), project, projectHelper, outDir);
            } catch (IOException e) {
                throw new MojoFailureException("Error generating archetype catalog due " + e.getMessage(), e);
            }
        }
    }

    public static void generateArchetypeCatalog(Log log, MavenProject project, MavenProjectHelper projectHelper, File outDir) throws MojoExecutionException, IOException {

        File archetypes = PackageHelper.findCamelDirectory(project.getBasedir(), "archetypes");
        if (archetypes == null || !archetypes.exists()) {
            throw new MojoExecutionException("Cannot find directory: archetypes");
        }
        log.info("Scanning for Camel Maven Archetypes from directory: " + archetypes);

        // find all archetypes which are in the parent dir of the build dir
        File[] dirs = archetypes.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.getName().startsWith("camel-archetype") && pathname.isDirectory();
            }
        });

        List<ArchetypeModel> models = new ArrayList<>();

        for (File dir : dirs) {
            File pom = new File(dir, "pom.xml");
            if (!pom.exists() && !pom.isFile()) {
                continue;
            }

            boolean parent = false;
            ArchetypeModel model = new ArchetypeModel();

            // just use a simple line by line text parser (no need for DOM) just
            // to grab 4 lines of data
            for (Object o : FileUtils.readLines(pom, StandardCharsets.UTF_8)) {

                String line = o.toString();

                // we only want to read version from parent
                if (line.contains("<parent>")) {
                    parent = true;
                    continue;
                }
                if (line.contains("</parent>")) {
                    parent = false;
                    continue;
                }
                if (parent) {
                    // grab version from parent
                    String version = Strings.between(line, "<version>", "</version>");
                    if (version != null) {
                        model.setVersion(version);
                    }
                    continue;
                }

                String groupId = Strings.between(line, "<groupId>", "</groupId>");
                String artifactId = Strings.between(line, "<artifactId>", "</artifactId>");
                String description = Strings.between(line, "<description>", "</description>");

                if (groupId != null && model.getGroupId() == null) {
                    model.setGroupId(groupId);
                }
                if (artifactId != null && model.getArtifactId() == null) {
                    model.setArtifactId(artifactId);
                }
                if (description != null && model.getDescription() == null) {
                    model.setDescription(description);
                }
            }

            if (model.getGroupId() != null && model.getArtifactId() != null && model.getVersion() != null) {
                models.add(model);
            }
        }

        log.info("Found " + models.size() + " archetypes");

        if (!models.isEmpty()) {

            // make sure there is a dir
            outDir.mkdirs();

            File out = new File(outDir, "archetype-catalog.xml");
            FileOutputStream fos = new FileOutputStream(out, false);

            // write top
            String top = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<archetype-catalog>\n  <archetypes>";
            fos.write(top.getBytes());

            // write each archetype
            for (ArchetypeModel model : models) {
                fos.write("\n    <archetype>".getBytes());
                fos.write(("\n      <groupId>" + model.getGroupId() + "</groupId>").getBytes());
                fos.write(("\n      <artifactId>" + model.getArtifactId() + "</artifactId>").getBytes());
                fos.write(("\n      <version>" + model.getVersion() + "</version>").getBytes());
                if (model.getDescription() != null) {
                    fos.write(("\n      <description>" + model.getDescription() + "</description>").getBytes());
                }
                fos.write("\n    </archetype>".getBytes());
            }

            // write bottom
            String bottom = "\n  </archetypes>\n</archetype-catalog>\n";
            fos.write(bottom.getBytes());

            fos.close();

            log.info("Saved archetype catalog to file " + out);

            try {
                if (projectHelper != null) {
                    log.info("Attaching archetype catalog to Maven project: " + project.getArtifactId());

                    List<String> includes = new ArrayList<>();
                    includes.add("archetype-catalog.xml");
                    projectHelper.addResource(project, outDir.getPath(), includes, new ArrayList<>());
                    projectHelper.attachArtifact(project, "xml", "archetype-catalog", out);
                }
            } catch (Exception e) {
                throw new MojoExecutionException("Failed to attach artifact to Maven project. Reason: " + e, e);
            }
        }
    }

    private static class ArchetypeModel {

        private String groupId;
        private String artifactId;
        private String version;
        private String description;

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

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

}
