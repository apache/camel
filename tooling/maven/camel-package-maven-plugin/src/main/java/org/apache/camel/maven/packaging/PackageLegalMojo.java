/**
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
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

/**
 * Analyses the Camel plugins in a project and generates legal files.
 */
@Mojo(name = "generate-legal", threadSafe = true, defaultPhase = LifecyclePhase.PREPARE_PACKAGE)
public class PackageLegalMojo extends AbstractGeneratorMojo {

    /**
     * The output directory for generated components file
     */
    @Parameter(defaultValue = "${project.build.directory}/classes")
    protected File legalOutDir;

    /**
     * Execute goal.
     *
     * @throws MojoExecutionException execution of the main class or one of the
     *                 threads it generated failed.
     * @throws MojoFailureException something bad happened...
     */
    public void execute() throws MojoExecutionException, MojoFailureException {
        processLegal(legalOutDir.toPath());
    }

    public void processLegal(Path legalOutDir) throws MojoExecutionException {
        // Only take care about camel legal stuff
        if (!"org.apache.camel".equals(project.getGroupId())) {
            return;
        }

        try (InputStream isLicense = getClass().getResourceAsStream("/camel-LICENSE.txt")) {
            String license = IOUtils.toString(isLicense, StandardCharsets.UTF_8);
            updateResource(legalOutDir.resolve("META-INF").resolve("LICENSE.txt"), license);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write legal files. Reason: " + e, e);
        }
        try (InputStream isNotice = getClass().getResourceAsStream("/camel-NOTICE.txt")) {
            String notice = IOUtils.toString(isNotice, StandardCharsets.UTF_8);
            updateResource(legalOutDir.resolve("META-INF").resolve("NOTICE.txt"), notice);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write legal files. Reason: " + e, e);
        }
    }

}
