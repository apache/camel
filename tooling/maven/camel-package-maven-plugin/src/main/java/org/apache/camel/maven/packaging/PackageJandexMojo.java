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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexWriter;
import org.jboss.jandex.Indexer;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * Generate a Jandex index for classes compiled as part of the current project.
 *
 * @author jdcasey
 */
@Mojo(name = "jandex", defaultPhase = LifecyclePhase.PROCESS_CLASSES, threadSafe = true)
public class PackageJandexMojo extends AbstractGeneratorMojo {

    @Parameter(defaultValue = "${project.build.outputDirectory}")
    protected File classesDirectory;

    /**
     * The name of the index file. Default's to
     * 'target/classes/META-INF/jandex.idx'
     */
    @Parameter(defaultValue = "${project.build.directory}/classes/META-INF/jandex.idx")
    private File index;

    @Parameter(defaultValue = "${showStaleFiles}")
    private boolean showStaleFiles;

    @Override
    public void execute(MavenProject project, MavenProjectHelper projectHelper, BuildContext buildContext) throws MojoFailureException, MojoExecutionException {
        classesDirectory = new File(project.getBuild().getOutputDirectory());
        index = new File(project.getBuild().getOutputDirectory(), "META-INF/jandex.idx");
        showStaleFiles = Boolean.parseBoolean(project.getProperties().getProperty("showStaleFiles", "false"));
        super.execute(project, projectHelper, buildContext);
    }

    @Override
    public void execute() throws MojoExecutionException {
        if (!classesDirectory.isDirectory()) {
            return;
        }
        try {
            List<Path> inputs = Files.walk(classesDirectory.toPath()).filter(f -> f.getFileName().toString().endsWith(".class")).collect(Collectors.toList());
            if (index.exists()) {
                long lastmod = lastmod(index.toPath());
                String stale = inputs.stream().filter(p -> lastmod(p) > lastmod).map(Path::toString).collect(Collectors.joining(", "));
                if (!stale.isEmpty()) {
                    getLog().info("Stale files detected, re-generating index.");
                    if (showStaleFiles) {
                        getLog().info("Stale files: " + stale);
                    } else if (getLog().isDebugEnabled()) {
                        getLog().debug("Stale files: " + stale);
                    }
                } else {
                    // everything is in order, skip
                    getLog().info("Skipping index generation, everything is up to date.");
                    return;
                }
            }

            getLog().info("Building index...");
            final Indexer indexer = new Indexer();
            for (Path file : inputs) {
                try (InputStream fis = Files.newInputStream(file)) {
                    indexer.index(fis);
                }
            }
            Index idx = indexer.complete();
            index.getParentFile().mkdirs();
            try (OutputStream os = new FileOutputStream(index)) {
                new IndexWriter(os).write(idx);
            }
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private long lastmod(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

}
