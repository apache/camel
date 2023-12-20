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
import java.util.Objects;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.camel.tooling.util.Strings;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.build.BuildContext;

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
        } else if (count == 0 && new File(project.getBasedir(), "src/main/kotlin").isDirectory()) {
            // camel-kotlin-dsl is not java based so check for kotlin source
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
                    "<dependency>\\s*<groupId>(?<groupId>.*)</groupId>\\s*<artifactId>(?<artifactId>.*)</artifactId>\\s*<version>(?<version>.*)</version>");
            Matcher matcher = pattern.matcher(between);

            TreeSet<MavenGav> dependencies = new TreeSet<>();
            while (matcher.find()) {
                String v = matcher.groupCount() > 2 ? matcher.group(3) : project.getVersion();
                MavenGav gav = new MavenGav(matcher.group(1), matcher.group(2), v, null);
                dependencies.add(gav);
            }
            // add ourselves
            dependencies.add(new MavenGav(
                    project.getGroupId(), project.getArtifactId(), "${project.version}", project.getArtifact().getType()));

            // generate string output of all dependencies
            String s = dependencies.stream()
                    .map(g -> g.asString("            "))
                    .collect(Collectors.joining("\n"));
            final String updatedPom = before + startDependenciesMarker
                                      + "\n" + s + "\n"
                                      + "        " + endDependenciesMarker + after;

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

            // load existing dependencies
            Pattern pattern = Pattern.compile(
                    "<dependency>\\s*<groupId>(?<groupId>.*)</groupId>\\s*<artifactId>(?<artifactId>.*)</artifactId>");
            Matcher matcher = pattern.matcher(between);
            TreeSet<MavenGav> dependencies = new TreeSet<>();
            while (matcher.find()) {
                MavenGav gav = new MavenGav(matcher.group(1), matcher.group(2), "${project.version}", null);
                dependencies.add(gav);
            }
            // add ourselves
            dependencies.add(new MavenGav(project.getGroupId(), project.getArtifactId(), "${project.version}", null));

            // generate string output of all dependencies
            String s = dependencies.stream()
                    // skip maven plugins
                    .filter(g -> !g.artifactId.contains("-maven-plugin"))
                    .map(g -> g.asString("        "))
                    .collect(Collectors.joining("\n"));
            final String updatedPom = before + startDependenciesMarker
                                      + "\n" + s + "\n"
                                      + "    " + endDependenciesMarker + after;

            updateResource(buildContext, pomFile, updatedPom);
        } catch (IOException e) {
            throw new MojoExecutionException("Error reading file " + pomFile + " Reason: " + e, e);
        }
    }

    private static class MavenGav implements Comparable<MavenGav> {
        private final String groupId;
        private final String artifactId;
        private final String version;
        private final String type;

        public MavenGav(String groupId, String artifactId, String version, String type) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.type = type;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            MavenGav mavenGav = (MavenGav) o;

            if (!groupId.equals(mavenGav.groupId)) {
                return false;
            }
            if (!artifactId.equals(mavenGav.artifactId)) {
                return false;
            }
            if (!version.equals(mavenGav.version)) {
                return false;
            }
            return Objects.equals(type, mavenGav.type);
        }

        @Override
        public int hashCode() {
            int result = groupId.hashCode();
            result = 31 * result + artifactId.hashCode();
            result = 31 * result + version.hashCode();
            result = 31 * result + (type != null ? type.hashCode() : 0);
            return result;
        }

        public String asString(String pad) {
            StringBuilder sb = new StringBuilder();
            sb.append(pad).append("<dependency>\n");
            sb.append(pad).append("    <groupId>").append(groupId).append("</groupId>\n");
            sb.append(pad).append("    <artifactId>").append(artifactId).append("</artifactId>\n");
            sb.append(pad).append("    <version>").append(version).append("</version>\n");
            if (type != null) {
                sb.append(pad).append("    <type>").append(type).append("</type>\n");
            }
            sb.append(pad).append("</dependency>");
            return sb.toString();
        }

        @Override
        public int compareTo(MavenGav o) {
            int n = groupId.compareTo(o.groupId);
            if (n == 0) {
                n = artifactId.compareTo(o.artifactId);
            }
            if (n == 0) {
                n = version.compareTo(o.version);
            }
            return n;
        }
    }

}
