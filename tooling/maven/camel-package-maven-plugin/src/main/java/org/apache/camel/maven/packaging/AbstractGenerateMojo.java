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
import java.io.IOError;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.sonatype.plexus.build.incremental.BuildContext;

public abstract class AbstractGenerateMojo extends AbstractMojo {

    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;
    @Component
    protected MavenProjectHelper projectHelper;
    @Component
    protected BuildContext buildContext;
    @Component
    private MavenSession session;
    @Parameter(defaultValue = "${showStaleFiles}")
    private boolean showStaleFiles;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            if (!isUpToDate(project)) {
                doExecute();
                writeIncrementalInfo(project);
            }
        } catch (Exception e) {
            throw new MojoFailureException("Error generating data " + e.toString(), e);
        }
    }

    protected abstract void doExecute() throws MojoFailureException, MojoExecutionException;

    protected void invoke(Class<? extends AbstractMojo> mojoClass) throws MojoExecutionException, MojoFailureException {
        try {
            AbstractMojo mojo = mojoClass.newInstance();
            mojo.setLog(getLog());
            mojo.setPluginContext(getPluginContext());
            ((AbstractGeneratorMojo) mojo).execute(project, projectHelper, buildContext);

        } catch (MojoExecutionException | MojoFailureException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoFailureException("Unable to create mojo", e);
        }
    }

    private void writeIncrementalInfo(MavenProject project) throws MojoExecutionException {
        try {
            Path cacheData = getIncrementalDataPath(project);
            String curdata = getIncrementalData();
            Files.createDirectories(cacheData.getParent());
            try (Writer w = Files.newBufferedWriter(cacheData)) {
                w.append(curdata);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Error checking manifest uptodate status", e);
        }
    }

    private boolean isUpToDate(MavenProject project) throws MojoExecutionException {
        try {
            Path cacheData = getIncrementalDataPath(project);
            String prvdata;
            if (Files.isRegularFile(cacheData)) {
                prvdata = new String(Files.readAllBytes(cacheData), StandardCharsets.UTF_8);
            } else {
                prvdata = null;
            }
            String curdata = getIncrementalData();
            if (curdata.equals(prvdata)) {
                long lastmod = Files.getLastModifiedTime(cacheData).toMillis();
                Set<String> stale = Stream.concat(Stream.concat(
                            project.getCompileSourceRoots().stream().map(File::new),
                            Stream.of(new File(project.getBuild().getOutputDirectory()))),
                            project.getArtifacts().stream().map(Artifact::getFile))
                        .flatMap(f -> newer(lastmod, f)).collect(Collectors.toSet());
                if (!stale.isEmpty()) {
                    getLog().info("Stale files detected, re-generating.");
                    if (showStaleFiles) {
                        getLog().info("Stale files: " + stale.stream().collect(Collectors.joining(", ")));
                    } else if (getLog().isDebugEnabled()) {
                        getLog().debug("Stale files: " + stale.stream().collect(Collectors.joining(", ")));
                    }
                } else {
                    // everything is in order, skip
                    getLog().info("Skipping generation, everything is up to date.");
                    return true;
                }
            } else {
                if (prvdata == null) {
                    getLog().info("No previous run data found, generating files.");
                } else {
                    getLog().info("Configuration changed, re-generating files.");
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Error checking uptodate status", e);
        }
        return false;
    }

    private String getIncrementalData() {
        return "";
    }

    private Path getIncrementalDataPath(MavenProject project) {
        return Paths.get(project.getBuild().getDirectory(), "camel-package-maven-plugin", "org.apache.camel_camel-package-maven-plugin_info_xx");
    }

    private long lastmod(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    private Stream<String> newer(long lastmod, File file) {
        try {
            if (file.isDirectory()) {
                return Files.walk(file.toPath()).filter(Files::isRegularFile).filter(p -> lastmod(p) > lastmod).map(Path::toString);
            } else if (file.isFile()) {
                if (lastmod(file.toPath()) > lastmod) {
                    if (file.getName().endsWith(".jar")) {
                        try (ZipFile zf = new ZipFile(file)) {
                            return zf.stream().filter(ze -> !ze.isDirectory()).filter(ze -> ze.getLastModifiedTime().toMillis() > lastmod)
                                    .map(ze -> file.toString() + "!" + ze.getName()).collect(Collectors.toList()).stream();
                        }
                    } else {
                        return Stream.of(file.toString());
                    }
                } else {
                    return Stream.empty();
                }
            } else {
                return Stream.empty();
            }
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

}
