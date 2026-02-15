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
package org.apache.camel.maven;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.springframework.boot.loader.tools.Library;
import org.springframework.boot.loader.tools.LibraryCallback;
import org.springframework.boot.loader.tools.LibraryScope;
import org.springframework.boot.loader.tools.Repackager;

/**
 * Maven plugin to repackage a JAR using Spring Boot loader tools to create a self-executing JAR. This creates a JAR
 * with the Spring Boot nested structure where dependencies are kept as separate JARs in BOOT-INF/lib/ and application
 * classes are in BOOT-INF/classes/.
 */
@Mojo(name = "repackage", defaultPhase = LifecyclePhase.PACKAGE,
      requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, threadSafe = true)
public class RepackageMojo extends AbstractMojo {

    /**
     * The Maven project.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * The source JAR file to repackage. If not specified, uses the project's main artifact.
     */
    @Parameter
    private File sourceJar;

    /**
     * The main class to use for the executable JAR.
     */
    @Parameter(required = true)
    private String mainClass;

    /**
     * The output directory for the repackaged JAR.
     */
    @Parameter(defaultValue = "${project.build.directory}")
    private File outputDirectory;

    /**
     * The final name of the repackaged JAR (without extension).
     */
    @Parameter(defaultValue = "${project.build.finalName}")
    private String finalName;

    /**
     * Whether to backup the source JAR.
     */
    @Parameter(defaultValue = "true")
    private boolean backupSource;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            File sourceJarFile = getSourceJar();
            if (!sourceJarFile.exists()) {
                throw new MojoFailureException("Source JAR does not exist: " + sourceJarFile);
            }

            getLog().info("Repackaging " + sourceJarFile + " using Spring Boot loader tools");

            Repackager repackager = new Repackager(sourceJarFile);
            repackager.setBackupSource(backupSource);
            repackager.setMainClass(mainClass);

            File targetFile = getTargetFile();
            repackager.repackage(targetFile, this::getLibraries);

            getLog().info("Successfully created self-executing JAR: " + targetFile);

        } catch (IOException e) {
            throw new MojoExecutionException("Failed to repackage JAR", e);
        }
    }

    private File getSourceJar() {
        if (sourceJar != null) {
            return sourceJar;
        }
        return new File(outputDirectory, finalName + ".jar");
    }

    private File getTargetFile() {
        return new File(outputDirectory, finalName + ".jar");
    }

    private void getLibraries(LibraryCallback callback) throws IOException {
        // Include all runtime and compile dependencies as separate JARs
        Set<Artifact> artifacts = project.getArtifacts();
        for (Artifact artifact : artifacts) {
            if (includeArtifact(artifact)) {
                File file = artifact.getFile();
                if (file != null && file.exists()) {
                    LibraryScope scope = getLibraryScope(artifact);
                    getLog().debug("Including dependency: " + artifact + " with scope: " + scope);
                    callback.library(new Library(file, scope));
                }
            }
        }
    }

    private boolean includeArtifact(Artifact artifact) {
        String scope = artifact.getScope();
        // Include compile and runtime dependencies
        return Artifact.SCOPE_COMPILE.equals(scope) ||
                Artifact.SCOPE_RUNTIME.equals(scope) ||
                (Artifact.SCOPE_PROVIDED.equals(scope) && artifact.getGroupId().startsWith("org.apache.camel"));
    }

    private LibraryScope getLibraryScope(Artifact artifact) {
        String scope = artifact.getScope();
        switch (scope) {
            case Artifact.SCOPE_COMPILE:
                return LibraryScope.COMPILE;
            case Artifact.SCOPE_RUNTIME:
                return LibraryScope.RUNTIME;
            case Artifact.SCOPE_PROVIDED:
                return LibraryScope.PROVIDED;
            default:
                return LibraryScope.COMPILE;
        }
    }
}
