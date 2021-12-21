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
package org.apache.camel.maven.sync.properties;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Properties;
import java.util.TreeMap;

import org.apache.camel.util.IOHelper;
import org.apache.camel.util.OrderedProperties;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Copy the properties in a POM in a different POM for syncing purpose.
 */
@Mojo(name = "sync-properties", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true)
public class SyncPropertiesMojo extends AbstractMojo {

    /**
     * The source pom template file.
     */
    @Parameter(defaultValue = "${basedir}/../../../parent/pom.xml")
    protected File sourcePom;

    /**
     * The pom file.
     */
    @Parameter(defaultValue = "${basedir}/../../../camel-dependencies/pom.xml")
    protected File targetPom;

    /**
     * The license header file.
     */
    @Parameter(defaultValue = "${basedir}/../../etc/apache-header.xml")
    protected File licenseHeader;

    /**
     * The Maven project
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            Properties parentProp;
            String generatedVersion;
            String artifactId = "camel-dependencies";

            getLog().info("Reading source file " + sourcePom.toPath());
            try (FileReader reader = new FileReader(sourcePom)) {
                MavenXpp3Reader mavenReader = new MavenXpp3Reader();
                Model model = mavenReader.read(reader);

                MavenProject sourceProject = new MavenProject(model);
                parentProp = sourceProject.getProperties();
                generatedVersion = sourceProject.getVersion();
            }

            getLog().info("Reading target file " + targetPom.toPath());
            try (FileReader reader = new FileReader(targetPom)) {
                MavenXpp3Reader mavenReader = new MavenXpp3Reader();
                Model model = mavenReader.read(reader);

                // lets sort the properties
                OrderedProperties op = new OrderedProperties();
                op.putAll(new TreeMap<>(parentProp));

                MavenProject targetProject = new MavenProject(model);
                targetProject.getModel().setProperties(op);
                artifactId = targetProject.getModel().getArtifactId();

                getLog().info("Set version of target pom to " + generatedVersion);
                targetProject.setVersion(generatedVersion);

                MavenXpp3Writer mavenWriter = new MavenXpp3Writer();
                mavenWriter.write(new FileWriter(targetPom), model);
            }

            // add license header in top
            getLog().info("Add license header...");
            String text = IOHelper.loadText(new FileInputStream(targetPom));
            String text2 = IOHelper.loadText(new FileInputStream(licenseHeader));
            StringBuffer sb = new StringBuffer(text);
            int pos = sb.indexOf("<project");
            sb.insert(pos, text2);

            // avoid annoying http -> https change when rebuilding
            getLog().info("Replacing xsd location ...");
            String out = sb.toString();
            out = out.replace("https://maven.apache.org/xsd/maven-4.0.0.xsd", "http://maven.apache.org/xsd/maven-4.0.0.xsd");

            // write lines
            getLog().info("Writing lines to " + targetPom.toPath());
            try (FileOutputStream outputStream = new FileOutputStream(targetPom)) {
                byte[] strToBytes = out.getBytes();
                outputStream.write(strToBytes);
            }

            // attach the modified pom to the build
            getLog().info("Attaching BOM artifact ...");
            getLog().info("Artifact: " + project.getGroupId() + ":" + artifactId + ":" + project.getVersion());
            DefaultArtifactHandler artifactHandler = new DefaultArtifactHandler("pom");
            Artifact artifact = new DefaultArtifact(
                    project.getGroupId(),
                    artifactId,
                    project.getVersion(),
                    "provided",
                    "pom",
                    null,
                    artifactHandler);
            artifact.setFile(targetPom);

            project.addAttachedArtifact(artifact);

            getLog().info("Finished.");
        } catch (Exception ex) {
            throw new MojoExecutionException("Cannot copy the properties between POMs", ex);
        }
    }

}
