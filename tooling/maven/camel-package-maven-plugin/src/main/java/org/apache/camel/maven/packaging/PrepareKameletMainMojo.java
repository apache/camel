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
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.camel.tooling.model.ArtifactModel;
import org.apache.camel.tooling.model.BaseModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.util.FileUtil;
import org.apache.camel.tooling.util.PackageHelper;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.build.BuildContext;

import static org.apache.camel.maven.packaging.generics.PackagePluginUtils.joinHeaderAndSource;
import static org.apache.camel.tooling.util.PackageHelper.findCamelDirectory;
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
     * build context to check changed files and mark them for refresh (used for m2e compatibility)
     */
    protected final BuildContext buildContext;

    /**
     * The camel-catalog directory
     */
    @Parameter(defaultValue = "${project.directory}/../../../catalog/camel-catalog")
    protected File catalogDir;

    @Parameter(defaultValue = "${project.directory}/../../../components")
    protected File componentsDir;

    @Parameter(defaultValue = "src/generated/")
    protected File genDir;

    private final Map<Path, BaseModel<?>> allModels = new HashMap<>();
    private String licenseHeader;

    @Inject
    public PrepareKameletMainMojo(BuildContext buildContext) {
        this.buildContext = buildContext;
    }

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

        try {
            updateKnownFactoryFinders();
        } catch (Exception e) {
            throw new MojoFailureException("Error updating camel-factoryfinder-known-dependencies.properties", e);
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

    protected void updateKnownFactoryFinders() throws Exception {
        List<String> lines = findFactoryFinder(componentsDir);

        // remove duplicate
        lines = lines.stream().distinct().collect(Collectors.toList());
        // and sort
        Collections.sort(lines);

        getLog().info("Found " + lines.size() + " FactoryFinder @JdkService");

        // load license header
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("license-header.txt")) {
            this.licenseHeader = loadText(is);
        } catch (Exception e) {
            throw new MojoFailureException("Error loading license-header.txt file", e);
        }

        String source = String.join("\n", lines) + "\n";
        writeSourceIfChanged(source, "resources", "camel-factoryfinder-known-dependencies.properties", genDir);
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

    private static List<String> findFactoryFinder(File rootDir) {
        List<String> answer = new ArrayList<>();

        for (File f : Objects.requireNonNull(rootDir.listFiles())) {
            String artifact = f.getName();
            if (artifact.startsWith("camel-")) {
                artifact = artifact.substring(6);
            }
            if (f.isDirectory()) {
                File fd = findCamelDirectory(f, "src/generated/resources/META-INF/services/org/apache/camel/");
                if (fd != null && fd.isDirectory()) {
                    findFactoryFinder(answer, artifact, fd, null);
                }
            }
        }

        return answer;
    }

    private static void findFactoryFinder(List<String> answer, String artifact, File dir, String subdir) {
        for (File sf : Objects.requireNonNull(dir.listFiles())) {
            if (sf.isFile() && !sf.getName().contains(".") && acceptFactoryFinderFile(sf.getName())) {
                // service marker file
                String path = subdir != null ? subdir + "/" + sf.getName() : sf.getName();
                answer.add("META-INF/services/org/apache/camel/" + path + "=camel:" + artifact);
            } else if (sf.isDirectory() && acceptFactoryFinderSubDir(sf.getName())) {
                findFactoryFinder(answer, artifact, sf, sf.getName());
            }
        }
    }

    private static boolean acceptFactoryFinderSubDir(String name) {
        return "cloud".equals(name) || "platform-http".equals(name) || "periodic-task".equals(name)
                || "resource-resolver".equals(name);
    }

    private static boolean acceptFactoryFinderFile(String name) {
        return !"TypeConverterLoader".equals(name) && !"reactive-executor".equals(name) && !"thread-pool-factory".equals(name);
    }

}
