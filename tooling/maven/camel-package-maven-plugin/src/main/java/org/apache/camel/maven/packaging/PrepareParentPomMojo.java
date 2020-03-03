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
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.camel.tooling.util.PackageHelper;
import org.apache.camel.tooling.util.Strings;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

/**
 * Prepares the parent/pom.xml to keep the Camel artifacts up-to-date.
 */
@Mojo(name = "prepare-parent-pom", threadSafe = true)
public class PrepareParentPomMojo extends AbstractMojo {

    /**
     * The maven project.
     */
    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;

    /**
     * The parent/pom
     */
    @Parameter(defaultValue = "${project.build.directory}/../../../parent/pom.xml")
    protected File parentPom;

    /**
     * The directory for components
     */
    @Parameter(defaultValue = "${project.build.directory}/../../../components")
    protected File componentsDir;

    /**
     * Maven ProjectHelper.
     */
    @Component
    private MavenProjectHelper projectHelper;

    /**
     * Execute goal.
     *
     * @throws MojoExecutionException execution of the main class or one of the
     *             threads it generated failed.
     * @throws MojoFailureException something bad happened...
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        updateParentPom("org.apache.camel", componentsDir, "camel components");
    }

    protected void updateParentPom(String groupId, File dir, String token) throws MojoExecutionException, MojoFailureException {
        SortedSet<String> artifactIds = new TreeSet<>();

        try {
            Set<File> poms = new HashSet<>();
            findComponentPoms(dir, poms);
            for (File pom : poms) {
                String aid = asArtifactId(pom);
                if (aid != null) {
                    artifactIds.add(aid);
                }
            }
        } catch (IOException e) {
            throw new MojoFailureException("Error due " + e.getMessage(), e);
        }

        getLog().debug("ArtifactIds: " + artifactIds);

        StringBuilder sb = new StringBuilder();
        for (String aid : artifactIds) {
            sb.append("      <dependency>\n");
            sb.append("        <groupId>" + groupId + "</groupId>\n");
            sb.append("        <artifactId>" + aid + "</artifactId>\n");
            sb.append("        <version>${project.version}</version>\n");
            sb.append("      </dependency>\n");
        }
        String changed = sb.toString();
        boolean updated = updateParentPom(parentPom, token, changed);

        if (updated) {
            getLog().info("Updated parent/pom.xml file");
        } else {
            getLog().debug("No changes to parent/pom.xml file");
        }
        getLog().info("parent/pom.xml contains " + artifactIds.size() + " " + token + " dependencies");
    }

    private void findComponentPoms(File parentDir, Set<File> components) {
        File[] files = parentDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory() && file.getName().startsWith("camel-")) {
                    findComponentPoms(file, components);
                } else if (parentDir.getName().startsWith("camel-") && file.getName().equals("pom.xml")) {
                    components.add(file);
                }
            }
        }
    }

    private String asArtifactId(File pom) throws IOException {
        String text = PackageHelper.loadText(pom);
        text = Strings.after(text, "</parent>");
        if (text != null) {
            text = Strings.between(text, "<artifactId>", "</artifactId>");
            return text;
        }
        return null;
    }

    private boolean updateParentPom(File file, String token, String changed) throws MojoExecutionException {
        String start = "<!-- " + token + ": START -->";
        String end = "<!-- " + token + ": END -->";

        if (!file.exists()) {
            return false;
        }

        try {
            String text = PackageHelper.loadText(file);

            String existing = Strings.between(text, start, end);
            if (existing != null) {
                // remove leading line breaks etc
                existing = existing.trim();
                changed = changed.trim();
                if (existing.equals(changed)) {
                    return false;
                } else {
                    String before = Strings.before(text, start);
                    String after = Strings.after(text, end);
                    text = before + start + "\n      " + changed + "\n      " + end + after;
                    PackageHelper.writeText(file, text);
                    return true;
                }
            } else {
                return false;
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error reading file " + file + " Reason: " + e, e);
        }
    }

}
