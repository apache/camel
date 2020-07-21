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
package org.apache.felix.bundleplugin;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.osgi.DefaultMaven2OsgiConverter;
import org.apache.maven.shared.osgi.Maven2OsgiConverter;

/**
 * Clean OSGi versions, ie convert a group of versions to OSGi format.
 */
@Mojo(name = "cleanVersions", threadSafe = true)
public class VersionCleanerPlugin extends AbstractMojo {

    /**
     * The BND instructions for the bundle.
     */
    @Parameter
    private Map<String, String> versions = new LinkedHashMap<String, String>();

    /**
     * The Maven project.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    private Maven2OsgiConverter maven2OsgiConverter = new DefaultMaven2OsgiConverter();

    public Maven2OsgiConverter getMaven2OsgiConverter() {
        return maven2OsgiConverter;
    }

    public void setMaven2OsgiConverter(Maven2OsgiConverter maven2OsgiConverter) {
        this.maven2OsgiConverter = maven2OsgiConverter;
    }

    public void execute() throws MojoExecutionException, MojoFailureException {
        for (String name : versions.keySet()) {
            String version = versions.get(name);
            String osgi = maven2OsgiConverter.getVersion(version);
            project.getProperties().put(name, osgi);
        }
    }
}
