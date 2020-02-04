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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.camel.maven.packaging.dsl.component.ComponentDslBuilderFactoryGenerator;
import org.apache.camel.maven.packaging.dsl.component.ComponentsBuilderFactoryGenerator;
import org.apache.camel.maven.packaging.dsl.component.ComponentsDslMetadataRegistry;
import org.apache.camel.maven.packaging.dsl.component.EnrichedComponentModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.util.PackageHelper;
import org.apache.camel.tooling.util.Strings;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import static org.apache.camel.tooling.util.PackageHelper.findCamelDirectory;
import static org.apache.camel.tooling.util.PackageHelper.loadText;

/**
 * Generate Endpoint DSL source files for Components.
 */
@Mojo(name = "generate-component-dsl", threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class ComponentDslMojo extends AbstractDslMojo {
    /**
     * The output directory
     */
    @Parameter
    protected File outputJavaDir;

    /**
     * Component DSL Pom file
     */
    @Parameter
    protected File componentDslPom;

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


    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            projectClassLoader = DynamicClassLoader.createDynamicClassLoader(project.getTestClasspathElements());
        } catch (org.apache.maven.artifact.DependencyResolutionRequiredException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        if (outputJavaDir == null) {
            outputJavaDir = findCamelDirectory(baseDir, "core/camel-componentdsl/src/main/java");
        }
        if (outputResourcesDir == null) {
            outputResourcesDir = findCamelDirectory(baseDir, "core/camel-componentdsl/src/main/resources");
        }
        if (componentDslPom == null) {
            componentDslPom = findCamelDirectory(baseDir, "core/camel-componentdsl").toPath().resolve("pom.xml").toFile();
        }
        if (componentsMetadata == null) {
            componentsMetadata = outputResourcesDir.toPath().resolve("metadata.json").toFile();
        }

        Map<File, Supplier<String>> files;

        try {
            files = Files.find(buildDir.toPath(), Integer.MAX_VALUE, (p, a) -> a.isRegularFile() && p.toFile().getName().endsWith(PackageHelper.JSON_SUFIX)).collect(Collectors.toMap(Path::toFile, s -> cache(() -> loadJson(s.toFile()))));
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
                for(ComponentModel model: compModels) {
                    // if more than one, we have a component class with multiple components aliases
                    createComponentDsl(new EnrichedComponentModel(model, compModels.size() > 1));
                }
            }
        }
    }

    private void createComponentDsl(final EnrichedComponentModel model) throws MojoExecutionException, MojoFailureException {
        // Create components DSL factories
        final ComponentDslBuilderFactoryGenerator componentDslBuilderFactoryGenerator = syncAndGenerateSpecificComponentsBuilderFactories(model);

        // Update components metadata
        final ComponentsDslMetadataRegistry componentsDslMetadataRegistry = syncAndUpdateComponentsMetadataRegistry(model, componentDslBuilderFactoryGenerator.getGeneratedClassName());

        final Set<EnrichedComponentModel> componentCachedModels = new LinkedHashSet<>(componentsDslMetadataRegistry.getComponentCacheFromMemory().values());

        // Create components DSL entry builder factories
        syncAndGenerateComponentsBuilderFactories(componentCachedModels);

        // Update componentsDsl pom file
        syncPomFile(componentDslPom, componentsDslMetadataRegistry.getComponentCacheFromMemory());
    }

    private ComponentDslBuilderFactoryGenerator syncAndGenerateSpecificComponentsBuilderFactories(final EnrichedComponentModel componentModel) throws MojoFailureException {
        final ComponentDslBuilderFactoryGenerator componentDslBuilderFactoryGenerator = ComponentDslBuilderFactoryGenerator.generateClass(componentModel, projectClassLoader, componentsDslPackageName);
        writeSourceIfChanged(componentDslBuilderFactoryGenerator.printClassAsString(), componentsDslFactoriesPackageName.replace('.', '/'), componentDslBuilderFactoryGenerator.getGeneratedClassName() + ".java", outputJavaDir);

        getLog().info("Regenerate " + componentDslBuilderFactoryGenerator.getGeneratedClassName());

        return componentDslBuilderFactoryGenerator;
    }

    private ComponentsDslMetadataRegistry syncAndUpdateComponentsMetadataRegistry(final EnrichedComponentModel componentModel, final String className) {
        final ComponentsDslMetadataRegistry componentsDslMetadataRegistry = new ComponentsDslMetadataRegistry(outputJavaDir.toPath().resolve(componentsDslFactoriesPackageName.replace('.', '/')).toFile(), componentsMetadata);
        componentsDslMetadataRegistry.addComponentToMetadataAndSyncMetadataFile(componentModel, className);

        getLog().info("Update components metadata with " + className);

        return componentsDslMetadataRegistry;
    }

    private void syncAndGenerateComponentsBuilderFactories(final Set<EnrichedComponentModel> componentCachedModels) throws MojoFailureException {
        final ComponentsBuilderFactoryGenerator componentsBuilderFactoryGenerator = ComponentsBuilderFactoryGenerator.generateClass(componentCachedModels, projectClassLoader, componentsDslPackageName);
        writeSourceIfChanged(componentsBuilderFactoryGenerator.printClassAsString(), componentsDslPackageName.replace('.', '/'), componentsBuilderFactoryGenerator.getGeneratedClassName() + ".java", outputJavaDir);

        getLog().info("Regenerate " + componentsBuilderFactoryGenerator.getGeneratedClassName());
    }

    private void syncPomFile(final File pomFile, final Map<String, EnrichedComponentModel> componentsModels) throws MojoExecutionException {
        final String startMainComponentImportMarker = "<!-- START: camel components import -->";
        final String endMainComponentImportMarker = "<!-- END: camel components import -->";

        if (!pomFile.exists()) {
            throw new MojoExecutionException("Pom file " + pomFile.getPath() + " does not exist");
        }

        try {
            final String pomText = loadText(pomFile);

            final String before = Strings.before(pomText, startMainComponentImportMarker).trim();
            final String after = Strings.after(pomText, endMainComponentImportMarker).trim();

            // generate unique Artifacts IDs
            final Map<String, List<ComponentModel>> uniqueArtifacts = componentsModels.values()
                    .stream()
                    .collect(Collectors.groupingBy(ComponentModel::getArtifactId, TreeMap::new, Collectors.toList()));

            final StringBuilder stringBuilder = new StringBuilder();

            uniqueArtifacts.forEach((artifactKey, groupedComponentsModel) -> stringBuilder.append(generateDependencyModule(groupedComponentsModel.get(0))));

            final String updatedPom = before + "\n\t\t" + startMainComponentImportMarker + "\n" + stringBuilder.toString() + "\t\t"
                    + endMainComponentImportMarker + "\n\t\t" + after;
            updateResource(buildContext, componentDslPom.toPath(), updatedPom);
        } catch (IOException e) {
            throw new MojoExecutionException("Error reading file " + pomFile + " Reason: " + e, e);
        }
    }

    private String generateDependencyModule(final ComponentModel model) {
        return "\t\t<dependency>\n"
                + "\t\t\t<groupId>" + model.getGroupId() + "</groupId>\n"
                + "\t\t\t<artifactId>" + model.getArtifactId() + "</artifactId>\n"
                + "\t\t\t<scope>provided</scope>\n"
                + "\t\t\t<version>${project.version}</version>\n"
                + "\t\t</dependency>\n";
    }
}
