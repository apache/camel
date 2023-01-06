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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.tooling.util.FileUtil;
import org.apache.camel.tooling.util.PackageHelper;
import org.apache.camel.util.IOHelper;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.ReaderFactory;

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

        try {
            String sourceStr = IOHelper.toString(ReaderFactory.newXmlReader(Files.newInputStream(sourcePom.toPath())));
            String targetStr = IOHelper.toString(ReaderFactory
                    .newXmlReader(SyncPropertiesMojo.class.getResourceAsStream("/camel-dependencies-template.xml")));

            String version = findGroup(sourceStr, "<parent>.*?(?<v><version>.*?</version>).*?</parent>", "v");
            String properties = findGroup(sourceStr, "(?<p><properties>.*?</properties>)", "p");

            version = version.replaceAll("\\$", "\\\\\\$");
            properties = properties.replaceAll("\\$", "\\\\\\$");

            targetStr = targetStr.replaceFirst("\\Q<version>@version@</version>\\E", version);
            targetStr = targetStr.replaceFirst("\\Q<properties>@properties@</properties>\\E", properties);

            // write lines
            boolean updated = FileUtil.updateFile(targetPom.toPath(), targetStr, StandardCharsets.UTF_8);
            if (updated) {
                getLog().info("Updated: " + targetPom);
            }
            getLog().debug("Finished.");
        } catch (Exception ex) {
            throw new MojoExecutionException("Cannot copy the properties between POMs", ex);
        }
    }

    private String findGroup(String str, String regex, String group) {
        Matcher m = Pattern.compile(regex, Pattern.DOTALL).matcher(str);
        if (m.find()) {
            return m.group(group);
        }
        return str;
    }

}
