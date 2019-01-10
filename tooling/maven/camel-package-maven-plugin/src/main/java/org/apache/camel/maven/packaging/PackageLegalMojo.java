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
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Objects;

import org.apache.commons.io.IOUtils;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * Analyses the Camel plugins in a project and generates extra descriptor information for easier auto-discovery in Camel.
 */
@Mojo(name = "generate-legal", threadSafe = true, defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class PackageLegalMojo extends AbstractMojo {

    /**
     * The maven project.
     */
    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;

    /**
     * The output directory for generated components file
     */
    @Parameter(defaultValue = "${project.build.directory}/generated/camel/legal")
    protected File legalOutDir;

    /**
     * Maven ProjectHelper.
     */
    @Component
    private MavenProjectHelper projectHelper;

    /**
     * build context to check changed files and mark them for refresh (used for
     * m2e compatibility)
     */
    @Component
    private BuildContext buildContext;

    /**
     * Execute goal.
     *
     * @throws MojoExecutionException execution of the main class or one of the
     *                 threads it generated failed.
     * @throws MojoFailureException something bad happened...
     */
    public void execute() throws MojoExecutionException, MojoFailureException {
        processLegal(legalOutDir.toPath());

        projectHelper.addResource(project, legalOutDir.getPath(), Collections.singletonList("**/*"), Collections.emptyList());
    }

    public void processLegal(Path legalOutDir) {
        // Only take care about camel legal stuff
        if (!"org.apache.camel".equals(project.getGroupId())) {
            return;
        }
        boolean hasLicense = project.getResources().stream()
                .map(Resource::getDirectory)
                .map(Paths::get)
                .map(p -> p.resolve("META-INF").resolve("LICENSE.txt"))
                .anyMatch(Files::isRegularFile);
        if (!hasLicense) {
            try (InputStream isLicense = getClass().getResourceAsStream("/camel-LICENSE.txt")) {
                String license = IOUtils.toString(isLicense, StandardCharsets.UTF_8);
                updateResource(legalOutDir.resolve("META-INF").resolve("LICENSE.txt"), license);
            } catch (IOException e) {
                throw new IOError(e);
            }
        }
        boolean hasNotice = project.getResources().stream()
                .map(Resource::getDirectory)
                .map(Paths::get)
                .map(p -> p.resolve("META-INF").resolve("NOTICE.txt"))
                .anyMatch(Files::isRegularFile);
        if (!hasNotice) {
            try (InputStream isNotice = getClass().getResourceAsStream("/camel-NOTICE.txt")) {
                String notice = IOUtils.toString(isNotice, StandardCharsets.UTF_8);
                updateResource(legalOutDir.resolve("META-INF").resolve("NOTICE.txt"), notice);
            } catch (IOException e) {
                throw new IOError(e);
            }
        }
    }

    protected void updateResource(Path out, String data) {
        try {
            if (data == null) {
                if (Files.isRegularFile(out)) {
                    Files.delete(out);
                    refresh(out);
                }
            } else {
                if (Files.isRegularFile(out) && Files.isReadable(out)) {
                    String content = new String(Files.readAllBytes(out), StandardCharsets.UTF_8);
                    if (Objects.equals(content, data)) {
                        return;
                    }
                }
                Files.createDirectories(out.getParent());
                try (Writer w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
                    w.append(data);
                }
                refresh(out);
            }
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    protected void refresh(Path file) {
        if (buildContext != null) {
            buildContext.refresh(file.toFile());
        }
    }

}
