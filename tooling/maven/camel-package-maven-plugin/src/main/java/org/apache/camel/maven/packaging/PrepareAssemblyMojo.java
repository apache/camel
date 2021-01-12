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
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

import static org.apache.camel.tooling.util.PackageHelper.loadText;

/**
 * Prepares the apache-camel/pom.xml and common-bin to keep the Camel artifacts up-to-date.
 */
@Mojo(name = "prepare-assembly", threadSafe = true)
public class PrepareAssemblyMojo extends AbstractMojo {

    /**
     * The maven project.
     */
    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;

    /**
     * The apache-camel/pom
     */
    @Parameter(defaultValue = "${project.build.directory}/../../../apache-camel/pom.xml")
    protected File releasePom;

    /**
     * The apache-camel/descriptors/common-bin.xml
     */
    @Parameter(defaultValue = "${project.build.directory}/../../../apache-camel/src/main/descriptors/common-bin.xml")
    protected File commonBinXml;

    /**
     * The directory for components
     */
    @Parameter(defaultValue = "${project.build.directory}/../../../core/camel-allcomponents/pom.xml")
    protected File allComponentsPomFile;

    /**
     * Maven ProjectHelper.
     */
    @Component
    private MavenProjectHelper projectHelper;

    /**
     * Execute goal.
     *
     * @throws MojoExecutionException execution of the main class or one of the threads it generated failed.
     * @throws MojoFailureException   something bad happened...
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        updatePomAndCommonBin(allComponentsPomFile, "org.apache.camel", "camel components");
    }

    protected void updatePomAndCommonBin(File allComponentsPom, String groupId, String token)
            throws MojoExecutionException, MojoFailureException {
        SortedSet<String> artifactIds = new TreeSet<>();

        final String pomText;
        try {
            pomText = loadText(allComponentsPom);
        } catch (IOException e) {
            throw new MojoExecutionException("Error loading camel-allcomponents pom.xml file", e);
        }

        final String before = Strings.before(pomText, "<dependencies>");
        final String after = Strings.after(pomText, "</dependencies>");

        final String between = pomText.substring(before.length(), pomText.length() - after.length());

        Pattern pattern = Pattern.compile(
                "<dependency>\\s*<groupId>(?<groupId>.*)</groupId>\\s*<artifactId>(?<artifactId>.*)</artifactId>\\s*</dependency>");
        Matcher matcher = pattern.matcher(between);
        TreeSet<String> dependencies = new TreeSet<>();
        while (matcher.find()) {
            artifactIds.add(matcher.group(2));
        }

        getLog().debug("ArtifactIds: " + artifactIds);

        // update pom.xml
        StringBuilder sb = new StringBuilder();
        for (String aid : artifactIds) {
            sb.append("    <dependency>\n");
            sb.append("      <groupId>" + groupId + "</groupId>\n");
            sb.append("      <artifactId>" + aid + "</artifactId>\n");
            sb.append("      <version>${project.version}</version>\n");
            sb.append("    </dependency>\n");
        }
        String changed = sb.toString();
        boolean updated = updateXmlFile(releasePom, token, changed, "    ");

        if (updated) {
            getLog().info("Updated apache-camel/pom.xml file");
        } else {
            getLog().debug("No changes to apache-camel/pom.xml file");
        }
        getLog().info("apache-camel/pom.xml contains " + artifactIds.size() + " " + token + " dependencies");

        // update common-bin.xml
        sb = new StringBuilder();
        for (String aid : artifactIds) {
            sb.append("        <include>" + groupId + ":" + aid + "</include>\n");
        }
        changed = sb.toString();
        updated = updateXmlFile(commonBinXml, token, changed, "        ");

        if (updated) {
            getLog().info("Updated apache-camel/src/main/descriptors/common-bin.xml file");
        } else {
            getLog().debug("No changes to apache-camel/src/main/descriptors/common-bin.xml file");
        }
        getLog().info("apache-camel/src/main/descriptors/common-bin.xml contains " + artifactIds.size() + " " + token
                      + " dependencies");
    }

    private boolean updateXmlFile(File file, String token, String changed, String spaces) throws MojoExecutionException {
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
                    text = before + start + "\n" + spaces + changed + "\n" + spaces + end + after;
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
