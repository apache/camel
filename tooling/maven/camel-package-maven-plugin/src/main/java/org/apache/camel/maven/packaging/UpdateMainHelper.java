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

import org.apache.camel.tooling.util.FileUtil;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import static org.apache.camel.tooling.util.PackageHelper.findCamelDirectory;

/**
 * Updates camel-main with the known component, data-format, and language names.
 */
@Mojo(name = "update-main-helper", threadSafe = true)
public class UpdateMainHelper extends AbstractGeneratorMojo {

    @Parameter(defaultValue = "${project.basedir}/src/generated/resources/org/apache/camel/catalog/")
    protected File propertiesDir;

    @Parameter(defaultValue = "${project.basedir}/")
    protected File baseDir;

    /**
     * Execute goal.
     *
     * @throws MojoExecutionException execution of the main class or one of the threads it generated failed.
     */
    @Override
    public void execute() throws MojoExecutionException {
        File camelDir = findCamelDirectory(baseDir, "core/camel-main");
        if (camelDir == null) {
            getLog().debug("No core/camel-main folder found, skipping execution");
            return;
        }

        try {
            File targetDir = new File(camelDir, "src/generated/resources/org/apache/camel/main");

            File source = new File(propertiesDir, "components.properties");
            File target = new File(targetDir, "components.properties");
            FileUtil.updateFile(source.toPath(), target.toPath());

            source = new File(propertiesDir, "dataformats.properties");
            target = new File(targetDir, "dataformats.properties");
            FileUtil.updateFile(source.toPath(), target.toPath());

            source = new File(propertiesDir, "languages.properties");
            target = new File(targetDir, "languages.properties");
            FileUtil.updateFile(source.toPath(), target.toPath());

        } catch (Exception e) {
            throw new MojoExecutionException("Error updating camel-main", e);
        }
    }

}
