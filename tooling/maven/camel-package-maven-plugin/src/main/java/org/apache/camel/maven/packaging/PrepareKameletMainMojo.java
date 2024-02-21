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
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.camel.tooling.model.ArtifactModel;
import org.apache.camel.tooling.model.BaseModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.util.FileUtil;
import org.apache.camel.tooling.util.PackageHelper;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.build.BuildContext;

import static org.apache.camel.maven.packaging.generics.PackagePluginUtils.joinHeaderAndSource;
import static org.apache.camel.tooling.util.PackageHelper.loadText;

/**
 * Prepares camel-kamelet-main
 */
@Mojo(name = "prepare-kamelet-main", threadSafe = true)
public class PrepareKameletMainMojo extends AbstractMojo {

    /**
     * The maven project.
     */
    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;

    /**
     * Maven ProjectHelper.
     */
    @Component
    protected MavenProjectHelper projectHelper;

    /**
     * build context to check changed files and mark them for refresh (used for m2e compatibility)
     */
    @Component
    protected BuildContext buildContext;

    /**
     * The camel-catalog directory
     */
    @Parameter(defaultValue = "${project.directory}/../../../catalog/camel-catalog")
    protected File catalogDir;

    @Parameter(defaultValue = "src/generated/")
    protected File genDir;

    private final Map<Path, BaseModel<?>> allModels = new HashMap<>();
    private transient String licenseHeader;

    /**
     * Execute goal.
     *
     * @throws MojoExecutionException execution of the main class or one of the threads it generated failed.
     * @throws MojoFailureException   something bad happened...
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            updateKnownDependencies();
        } catch (Exception e) {
            throw new MojoFailureException("Error updating camel-component-known-dependencies.properties", e);
        }
    }

    protected void updateKnownDependencies() throws Exception {
        Collection<Path> allJsonFiles = new TreeSet<>();

        File path = new File(catalogDir, "src/generated/resources/org/apache/camel/catalog/components");
        for (File p : path.listFiles()) {
            String f = p.getName();
            if (f.endsWith(PackageHelper.JSON_SUFIX)) {
                allJsonFiles.add(p.toPath());
            }
        }

        for (Path p : allJsonFiles) {
            var m = JsonMapper.generateModel(p);
            if (m != null) {
                allModels.put(p, m);
            }
        }

        List<String> lines = new ArrayList<>();
        for (BaseModel<?> model : allModels.values()) {
            String fqn = model.getJavaType();
            if (model instanceof ArtifactModel) {
                String aid = ((ArtifactModel<?>) model).getArtifactId();
                if (aid.startsWith("camel-")) {
                    aid = aid.substring(6);
                }
                String line = fqn + "=camel:" + aid;
                lines.add(line);
            }
        }
        // remove duplicate
        lines = lines.stream().distinct().collect(Collectors.toList());
        // and sort
        Collections.sort(lines);

        // load license header
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("license-header.txt")) {
            this.licenseHeader = loadText(is);
        } catch (Exception e) {
            throw new MojoFailureException("Error loading license-header.txt file", e);
        }

        String source = String.join("\n", lines) + "\n";
        writeSourceIfChanged(source, "resources", "camel-component-known-dependencies.properties", genDir);
    }

    protected boolean writeSourceIfChanged(String source, String filePath, String fileName, File outputDir)
            throws MojoFailureException {
        Path target = outputDir.toPath().resolve(filePath).resolve(fileName);

        try {
            final String code = joinHeaderAndSource(licenseHeader, source);

            if (getLog().isDebugEnabled()) {
                getLog().debug("Source code generated:\n" + code);
            }

            return updateResource(buildContext, target, code);
        } catch (Exception e) {
            throw new MojoFailureException("IOError with file " + target, e);
        }
    }

    public static boolean updateResource(BuildContext buildContext, Path out, String data) {
        try {
            if (FileUtil.updateFile(out, data)) {
                refresh(buildContext, out);
                return true;
            }
        } catch (IOException e) {
            throw new IOError(e);
        }
        return false;
    }

    public static void refresh(BuildContext buildContext, Path file) {
        if (buildContext != null) {
            buildContext.refresh(file.toFile());
        }
    }

}
