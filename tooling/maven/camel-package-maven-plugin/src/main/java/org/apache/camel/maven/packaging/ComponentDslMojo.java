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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.camel.maven.packaging.dsl.component.ComponentDslBuilderFactoryGenerator;
import org.apache.camel.maven.packaging.dsl.component.ComponentsBuilderFactoryGenerator;
import org.apache.camel.maven.packaging.dsl.component.ComponentsDslMetadataRegistry;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.util.PackageHelper;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.sonatype.plexus.build.incremental.BuildContext;

import static org.apache.camel.tooling.util.PackageHelper.findCamelDirectory;
import static org.apache.camel.tooling.util.PackageHelper.loadText;

/**
 * Generate Endpoint DSL source files for Components.
 */
@Mojo(name = "generate-component-dsl", threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class ComponentDslMojo extends AbstractGeneratorMojo {
    /**
     * The project build directory
     */
    @Parameter(defaultValue = "${project.build.directory}")
    protected File buildDir;

    /**
     * The base directory
     */
    @Parameter(defaultValue = "${project.basedir}")
    protected File baseDir;

    /**
     * The output directory
     */
    @Parameter
    protected File sourcesOutputDir;

    /**
     * Component Metadata file
     */
    @Parameter
    protected File componentsMetadata;

    /**
     * Components DSL Metadata
     */
    @Parameter
    protected File outputResourcesDir;

    /**
     * The package where to the main DSL component package is
     */
    @Parameter(defaultValue = "org.apache.camel.builder.component")
    protected String componentsDslPackageName;

    /**
     * The package where to generate component DSL specific factories
     */
    @Parameter(defaultValue = "org.apache.camel.builder.component.dsl")
    protected String componentsDslFactoriesPackageName;

    DynamicClassLoader projectClassLoader;

    @Override
    public void execute(MavenProject project, MavenProjectHelper projectHelper, BuildContext buildContext) throws MojoFailureException, MojoExecutionException {
        buildDir = new File(project.getBuild().getDirectory());
        baseDir = project.getBasedir();
        componentsDslPackageName = "org.apache.camel.builder.component";
        componentsDslFactoriesPackageName = "org.apache.camel.builder.component.dsl";
        super.execute(project, projectHelper, buildContext);
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            projectClassLoader = DynamicClassLoader.createDynamicClassLoader(project.getTestClasspathElements());
        } catch (org.apache.maven.artifact.DependencyResolutionRequiredException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        Path root = findCamelDirectory(baseDir, "core/camel-componentdsl").toPath();
        if (sourcesOutputDir == null) {
            sourcesOutputDir = root.resolve("src/generated/java").toFile();
        }
        if (outputResourcesDir == null) {
            outputResourcesDir = root.resolve("src/generated/resources").toFile();
        }
        if (componentsMetadata == null) {
            componentsMetadata = outputResourcesDir.toPath().resolve("metadata.json").toFile();
        }

        Map<File, Supplier<String>> files;

        try {
            files = Files.find(buildDir.toPath(), Integer.MAX_VALUE, (p, a) -> a.isRegularFile() && p.toFile().getName().endsWith(PackageHelper.JSON_SUFIX))
                    .collect(Collectors.toMap(Path::toFile, s -> cache(() -> loadJson(s.toFile()))));
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        executeComponent(files);
    }

    private void executeComponent(Map<File, Supplier<String>> jsonFiles) throws MojoExecutionException, MojoFailureException {
        // find the component names
        Set<String> componentNames = new TreeSet<>();
        findComponentNames(buildDir, componentNames);

        // create auto configuration for the components
        if (!componentNames.isEmpty()) {
            getLog().debug("Found " + componentNames.size() + " components");

            List<ComponentModel> allModels = new LinkedList<>();
            for (String componentName : componentNames) {
                String json = loadComponentJson(jsonFiles, componentName);
                if (json != null) {
                    ComponentModel model = JsonMapper.generateComponentModel(json);
                    allModels.add(model);
                }
            }

            // Group the models by implementing classes
            Map<String, List<ComponentModel>> grModels = allModels.stream().collect(Collectors.groupingBy(ComponentModel::getJavaType));
            for (String componentClass : grModels.keySet()) {
                List<ComponentModel> compModels = grModels.get(componentClass);
                for (ComponentModel model : compModels) {
                    // if more than one, we have a component class with multiple components aliases
                    createComponentDsl(model);
                }
            }
        }
    }

    private void createComponentDsl(final ComponentModel model) throws MojoExecutionException, MojoFailureException {
        // Create components DSL factories
        final ComponentDslBuilderFactoryGenerator componentDslBuilderFactoryGenerator = syncAndGenerateSpecificComponentsBuilderFactories(model);

        // Update components metadata
        final ComponentsDslMetadataRegistry componentsDslMetadataRegistry = syncAndUpdateComponentsMetadataRegistry(model, componentDslBuilderFactoryGenerator.getGeneratedClassName());

        final Set<ComponentModel> componentCachedModels = new TreeSet<>(
                Comparator.comparing(ComponentModel::getScheme)
        );
        componentCachedModels.addAll(componentsDslMetadataRegistry.getComponentCacheFromMemory().values());

        // Create components DSL entry builder factories
        syncAndGenerateComponentsBuilderFactories(componentCachedModels);
    }

    private ComponentDslBuilderFactoryGenerator syncAndGenerateSpecificComponentsBuilderFactories(final ComponentModel componentModel) throws MojoFailureException {
        final ComponentDslBuilderFactoryGenerator componentDslBuilderFactoryGenerator = ComponentDslBuilderFactoryGenerator.generateClass(componentModel, projectClassLoader, componentsDslPackageName);
        boolean updated = writeSourceIfChanged(componentDslBuilderFactoryGenerator.printClassAsString(), componentsDslFactoriesPackageName.replace('.', '/'), componentDslBuilderFactoryGenerator.getGeneratedClassName() + ".java", sourcesOutputDir);

        if (updated) {
            getLog().info("Updated ComponentDsl: " + componentModel.getScheme());
        }

        return componentDslBuilderFactoryGenerator;
    }

    private ComponentsDslMetadataRegistry syncAndUpdateComponentsMetadataRegistry(final ComponentModel componentModel, final String className) {
        final ComponentsDslMetadataRegistry componentsDslMetadataRegistry = new ComponentsDslMetadataRegistry(sourcesOutputDir.toPath().resolve(componentsDslFactoriesPackageName.replace('.', '/')).toFile(), componentsMetadata);
        boolean updated = componentsDslMetadataRegistry.addComponentToMetadataAndSyncMetadataFile(componentModel, className);

        if (updated) {
            getLog().info("Updated ComponentDsl metadata: " + componentModel.getScheme());
        }

        return componentsDslMetadataRegistry;
    }

    private void syncAndGenerateComponentsBuilderFactories(final Set<ComponentModel> componentCachedModels) throws MojoFailureException {
        final ComponentsBuilderFactoryGenerator componentsBuilderFactoryGenerator = ComponentsBuilderFactoryGenerator.generateClass(componentCachedModels, projectClassLoader, componentsDslPackageName);
        boolean updated = writeSourceIfChanged(componentsBuilderFactoryGenerator.printClassAsString(), componentsDslPackageName.replace('.', '/'), componentsBuilderFactoryGenerator.getGeneratedClassName() + ".java", sourcesOutputDir);

        if (updated) {
            getLog().info("Updated ComponentDsl factories: " + componentCachedModels);
        }
    }

    protected static String loadJson(File file) {
        try {
            return loadText(file);
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    protected static String loadComponentJson(Map<File, Supplier<String>> jsonFiles, String componentName) {
        return loadJsonOfType(jsonFiles, componentName, "component");
    }

    protected static String loadJsonOfType(Map<File, Supplier<String>> jsonFiles, String modelName, String type) {
        for (Map.Entry<File, Supplier<String>> entry : jsonFiles.entrySet()) {
            if (entry.getKey().getName().equals(modelName + ".json")) {
                String json = entry.getValue().get();
                if (json.contains("\"kind\": \"" + type + "\"")) {
                    return json;
                }
            }
        }
        return null;
    }

    protected void findComponentNames(File dir, Set<String> componentNames) {
        File f = new File(dir, "classes/META-INF/services/org/apache/camel/component");

        if (f.exists() && f.isDirectory()) {
            File[] files = f.listFiles();
            if (files != null) {
                for (File file : files) {
                    // skip directories as there may be a sub .resolver
                    // directory
                    if (file.isDirectory()) {
                        continue;
                    }
                    String name = file.getName();
                    if (name.charAt(0) != '.') {
                        componentNames.add(name);
                    }
                }
            }
        }
    }

    protected boolean writeSourceIfChanged(String source, String filePath, String fileName, File outputDir) throws MojoFailureException {
        Path target = outputDir.toPath().resolve(filePath).resolve(fileName);

        try {
            String header;
            try (InputStream is = getClass().getClassLoader().getResourceAsStream("license-header-java.txt")) {
                header = loadText(is);
            }
            String code = header + source;
            getLog().debug("Source code generated:\n" + code);

            return updateResource(buildContext, target, code);
        } catch (Exception e) {
            throw new MojoFailureException("IOError with file " + target, e);
        }
    }
}
