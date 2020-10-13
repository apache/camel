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
    protected File licenceHeader;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            Properties parentProp;
            try (FileReader reader = new FileReader(sourcePom)) {
                MavenXpp3Reader mavenReader = new MavenXpp3Reader();
                Model model = mavenReader.read(reader);

                MavenProject project = new MavenProject(model);
                parentProp = project.getProperties();
            }
            try (FileReader reader = new FileReader(targetPom)) {
                MavenXpp3Reader mavenReader = new MavenXpp3Reader();
                Model model = mavenReader.read(reader);

                // lets sort the properties
                OrderedProperties op = new OrderedProperties();
                op.putAll(new TreeMap<>(parentProp));

                MavenProject project = new MavenProject(model);
                project.getModel().setProperties(op);

                MavenXpp3Writer mavenWriter = new MavenXpp3Writer();
                mavenWriter.write(new FileWriter(targetPom), model);
            }

            // add license header in top
            String text = IOHelper.loadText(new FileInputStream(targetPom));
            String text2 = IOHelper.loadText(new FileInputStream(licenceHeader));
            StringBuffer sb = new StringBuffer(text);
            int pos = sb.indexOf("<project");
            sb.insert(pos, text2);

            // avoid annoying http -> https change when rebuilding
            String out = sb.toString();
            out = out.replace("https://maven.apache.org/xsd/maven-4.0.0.xsd", "http://maven.apache.org/xsd/maven-4.0.0.xsd");

            // write lines
            FileOutputStream outputStream = new FileOutputStream(targetPom);
            byte[] strToBytes = out.getBytes();
            outputStream.write(strToBytes);
            outputStream.close();
        } catch (Exception ex) {
            throw new MojoExecutionException("Cannot copy the properties between POMs", ex);
        }
    }

}
