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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.tooling.util.Strings;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.sonatype.plexus.build.incremental.BuildContext;

import static org.apache.camel.tooling.util.PackageHelper.findCamelDirectory;
import static org.apache.camel.tooling.util.PackageHelper.loadText;

/**
 * Prepares a Camel component analyzing if the maven module contains Camel
 * <ul>
 * <li>components</li>
 * <li>dataformats</li>
 * <li>languages</li>
 * <li>others</li>
 * </ul>
 * And for each of those generates extra descriptors and schema files for easier auto-discovery in Camel and tooling.
 */
@Mojo(name = "prepare-components", threadSafe = true)
public class PrepareComponentMojo extends AbstractGeneratorMojo {

    /**
     * The base directory
     */
    @Parameter(defaultValue = "${project.basedir}")
    protected File baseDir;

    /**
     * The output directory for generated components file
     */
    @Parameter(defaultValue = "${project.basedir}/src/generated/java")
    protected File configurerSourceOutDir;

    /**
     * The output directory for generated components file
     */
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources")
    protected File configurerResourceOutDir;

    /**
     * The output directory for generated components file
     */
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources")
    protected File componentOutDir;

    /**
     * The output directory for generated dataformats file
     */
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources")
    protected File dataFormatOutDir;

    /**
     * The output directory for generated languages file
     */
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources")
    protected File languageOutDir;

    /**
     * The output directory for generated others file
     */
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources")
    protected File otherOutDir;

    /**
     * The output directory for generated schema file
     */
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources")
    protected File schemaOutDir;

    /**
     * The project build directory
     */
    @Parameter(defaultValue = "${project.build.directory}")
    protected File buildDir;

    @Parameter(defaultValue = "${camel-prepare-component}")
    protected boolean prepareComponent;

    @Override
    public void execute(MavenProject project, MavenProjectHelper projectHelper, BuildContext buildContext)
            throws MojoFailureException, MojoExecutionException {
        configurerSourceOutDir = new File(project.getBasedir(), "src/generated/java");
        configurerResourceOutDir = componentOutDir
                = dataFormatOutDir = languageOutDir
                        = otherOutDir = schemaOutDir
                                = new File(project.getBasedir(), "src/generated/resources");
        buildDir = new File(project.getBuild().getDirectory());
        prepareComponent = Boolean.parseBoolean(project.getProperties().getProperty("camel-prepare-component", "false"));
        super.execute(project, projectHelper, buildContext);
    }

    /**
     * Execute goal.
     *
     * @throws org.apache.maven.plugin.MojoExecutionException execution of the main class or one of the threads it
     *                                                        generated failed.
     * @throws org.apache.maven.plugin.MojoFailureException   something bad happened...
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!prepareComponent) {
            return;
        }

        int count = 0;
        count += new PackageComponentMojo(
                getLog(), project, projectHelper, buildDir,
                componentOutDir, buildContext).prepareComponent();
        count += new PackageDataFormatMojo(
                getLog(), project, projectHelper, dataFormatOutDir, configurerSourceOutDir,
                configurerResourceOutDir, schemaOutDir, buildContext).prepareDataFormat();
        count += new PackageLanguageMojo(
                getLog(), project, projectHelper, buildDir, languageOutDir,
                schemaOutDir, buildContext).prepareLanguage();
        if (count == 0 && new File(project.getBasedir(), "src/main/java").isDirectory()) {
            // okay its not any of the above then its other
            new PackageOtherMojo(
                    getLog(), project, projectHelper, otherOutDir,
                    schemaOutDir, buildContext).prepareOthers();
            count = 1;
        }

        // whether to sync pom in allcomponents/parent
        Object syncComponents = project.getContextValue("syncPomFile");
        Object syncParent = syncComponents;
        if (!"false".equals(syncComponents)) {
            boolean components = isParentArtifact(project, "components");
            if (components) {
                syncComponents = "true";
            } else {
                syncComponents = "false";
            }
            // do not sync parent for core as its handled manual
            boolean core = isParentArtifact(project, "core");
            if (core) {
                syncParent = "false";
            } else {
                syncParent = "true";
            }
        }
        if (count > 0) {
            if ("true".equals(syncParent)) {
                // we can sync either components or dsl in parent
                boolean dsl = isParentArtifact(project, "dsl");
                String token = dsl ? "dsl" : "components";
                syncParentPomFile(token);
            }
            if ("true".equals(syncComponents)) {
                syncAllComponentsPomFile();
            }
        }
    }

    private static boolean isParentArtifact(MavenProject project, String name) {
        if (project != null) {
            Artifact artifact = project.getParentArtifact();
            if (artifact != null) {
                if (name.equals(artifact.getArtifactId())) {
                    return true;
                } else {
                    MavenProject parent = project.getParent();
                    return isParentArtifact(parent, name);
                }
            }
        }
        return false;
    }

    private void syncParentPomFile(String token) throws MojoExecutionException {
        Path root = findCamelDirectory(project.getBasedir(), "parent").toPath();
        Path pomFile = root.resolve("pom.xml");

        final String startDependenciesMarker = "<!-- camel " + token + ": START -->";
        final String endDependenciesMarker = "<!-- camel " + token + ": END -->";

        if (!Files.isRegularFile(pomFile)) {
            throw new MojoExecutionException("Pom file " + pomFile + " does not exist");
        }

        try {
            final String pomText = loadText(pomFile);

            final String before = Strings.before(pomText, startDependenciesMarker);
            final String after = Strings.after(pomText, endDependenciesMarker);

            final String between = pomText.substring(before.length(), pomText.length() - after.length());

            Pattern pattern = Pattern.compile(
                    "<dependency>\\s*<groupId>(?<groupId>.*)</groupId>\\s*<artifactId>(?<artifactId>.*)</artifactId>\\s*<version>(?<version>.*)</version>\\s*</dependency>");
            Matcher matcher = pattern.matcher(between);
            TreeSet<String> dependencies = new TreeSet<>();
            while (matcher.find()) {
                dependencies.add(matcher.group());
            }

            String d = "<dependency>\n"
                       + "                <groupId>" + project.getGroupId() + "</groupId>\n"
                       + "                <artifactId>" + project.getArtifactId() + "</artifactId>\n"
                       + "                <version>${project.version}</version>\n";
            String type = project.getArtifact().getType();
            if (type != null && !"jar".equals(type) && !"maven-plugin".equals(type)) {
                d += "                <type>" + type + "</type>\n";
            }
            d += "            </dependency>";
            dependencies.add(d);

            final String updatedPom = before + startDependenciesMarker
                                      + "\n            "
                                      + String.join("\n            ", dependencies)
                                      + "\n            "
                                      + endDependenciesMarker + after;

            updateResource(buildContext, pomFile, updatedPom);
        } catch (IOException e) {
            throw new MojoExecutionException("Error reading file " + pomFile + " Reason: " + e, e);
        }
    }

    private void syncAllComponentsPomFile() throws MojoExecutionException {
        Path root = findCamelDirectory(project.getBasedir(), "catalog/camel-allcomponents").toPath();
        Path pomFile = root.resolve("pom.xml");

        final String startDependenciesMarker = "<dependencies>";
        final String endDependenciesMarker = "</dependencies>";

        if (!Files.isRegularFile(pomFile)) {
            throw new MojoExecutionException("Pom file " + pomFile + " does not exist");
        }

        try {
            final String pomText = loadText(pomFile);

            final String before = Strings.before(pomText, startDependenciesMarker);
            final String after = Strings.after(pomText, endDependenciesMarker);

            final String between = pomText.substring(before.length(), pomText.length() - after.length());

            Pattern pattern = Pattern.compile(
                    "<dependency>\\s*<groupId>(?<groupId>.*)</groupId>\\s*<artifactId>(?<artifactId>.*)</artifactId>\\s*</dependency>");
            Matcher matcher = pattern.matcher(between);
            TreeSet<String> dependencies = new TreeSet<>();
            while (matcher.find()) {
                dependencies.add(matcher.group());
            }

            String d = "<dependency>\n"
                       + "                <groupId>" + project.getGroupId() + "</groupId>\n"
                       + "                <artifactId>" + project.getArtifactId() + "</artifactId>\n"
                       + "                <version>${project.version}</version>\n";
            String type = project.getArtifact().getType();
            if (type != null && !"jar".equals(type) && !"maven-plugin".equals(type)) {
                d += "                <type>" + type + "</type>\n";
            }
            d += "            </dependency>";
            dependencies.add(d);

            final String updatedPom = before + startDependenciesMarker
                                      + "\n            "
                                      + String.join("\n            ", dependencies)
                                      + "\n            "
                                      + endDependenciesMarker + after;

            updateResource(buildContext, pomFile, updatedPom);
        } catch (IOException e) {
            throw new MojoExecutionException("Error reading file " + pomFile + " Reason: " + e, e);
        }
    }

}
