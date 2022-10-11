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
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.TreeMap;

import org.apache.camel.tooling.util.FileUtil;
import org.apache.camel.tooling.util.PackageHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.OrderedProperties;
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
 * Copy the properties from a source POM to a different destination POM for syncing purposes.
 */
@Mojo(name = "sync-properties", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true)
public class SyncPropertiesMojo extends AbstractMojo {

    /**
     * The base directory
     */
    @Parameter(defaultValue = "${project.basedir}")
    protected File baseDir;

    /**
     * The Maven project.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        File dir = PackageHelper.findCamelDirectory(baseDir, "parent");
        File sourcePom = new File(dir, "pom.xml");
        dir = PackageHelper.findCamelDirectory(baseDir, "camel-dependencies");
        File targetPom = new File(dir, "pom.xml");
        dir = PackageHelper.findCamelDirectory(baseDir, "etc");
        File licenseHeader = new File(dir, "apache-header.xml");

        try {
            Properties parentProp;
            String generatedVersion;

            if (getLog().isDebugEnabled()) {
                getLog().debug("Reading source file " + sourcePom.toPath());
            }

            try (FileReader reader = new FileReader(sourcePom)) {
                MavenXpp3Reader mavenReader = new MavenXpp3Reader();
                Model model = mavenReader.read(reader);

                MavenProject sourceProject = new MavenProject(model);
                parentProp = sourceProject.getProperties();
                generatedVersion = sourceProject.getVersion();
            }

            InputStream is = null;
            try {
                is = SyncPropertiesMojo.class.getResourceAsStream("/camel-dependencies-template.xml");
                MavenXpp3Reader mavenReader = new MavenXpp3Reader();
                Model model = mavenReader.read(is);

                // sort the properties
                OrderedProperties op = new OrderedProperties();
                op.putAll(new TreeMap<>(parentProp));

                MavenProject targetProject = new MavenProject(model);
                targetProject.getModel().setProperties(op);

                if (getLog().isDebugEnabled()) {
                    getLog().debug("Set version of target pom to " + generatedVersion);
                }
                targetProject.setVersion(generatedVersion);

                MavenXpp3Writer mavenWriter = new MavenXpp3Writer();
                mavenWriter.write(new FileWriter(targetPom), model);
            } finally {
                IOHelper.close(is);
            }

            // add license header in top
            getLog().debug("Add license header...");
            String text = IOHelper.loadText(new FileInputStream(targetPom));
            String text2 = IOHelper.loadText(new FileInputStream(licenseHeader));
            StringBuilder sb = new StringBuilder(text);
            int pos = sb.indexOf("<project");
            sb.insert(pos, text2);

            // avoid annoying http -> https change when rebuilding
            getLog().debug("Replacing xsd location ...");
            String out = sb.toString();
            out = out.replace("https://maven.apache.org/xsd/maven-4.0.0.xsd", "http://maven.apache.org/xsd/maven-4.0.0.xsd");

            // avoid IDE complaining about empty tag
            out = out.replace("<relativePath></relativePath>", "<relativePath />");

            // write lines
            boolean updated = FileUtil.updateFile(targetPom.toPath(), out, StandardCharsets.UTF_8);
            if (updated) {
                getLog().info("Updated: " + targetPom);
            }
            getLog().debug("Finished.");
        } catch (Exception ex) {
            throw new MojoExecutionException("Cannot copy the properties between POMs", ex);
        }
    }

}
