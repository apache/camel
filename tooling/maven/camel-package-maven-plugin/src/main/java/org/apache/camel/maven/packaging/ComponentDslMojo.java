package org.apache.camel.maven.packaging;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.camel.maven.packaging.dsl.component.ComponentDslGenerator;
import org.apache.camel.maven.packaging.dsl.component.ComponentDslMetadataGenerator;
import org.apache.camel.maven.packaging.model.ComponentModel;
import org.apache.camel.maven.packaging.model.ComponentOptionModel;
import org.apache.camel.maven.packaging.model.EndpointOptionModel;
import org.apache.camel.tooling.util.JSonSchemaHelper;
import org.apache.camel.tooling.util.PackageHelper;
import org.apache.camel.tooling.util.Strings;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import static org.apache.camel.tooling.util.JSonSchemaHelper.getSafeValue;
import static org.apache.camel.tooling.util.PackageHelper.*;
import static org.apache.camel.tooling.util.Strings.between;

/**
 * Generate Endpoint DSL source files for Components.
 */
@Mojo(name = "generate-component-dsl", threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class ComponentDslMojo extends AbstractGeneratorMojo {

    /**
     * The maven project.
     */
    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;

    /**
     * The project build directory
     */
    @Parameter(defaultValue = "${project.build.directory}")
    protected File buildDir;

    /**
     * The base directory
     */
    @Parameter(defaultValue = "${basedir}")
    protected File baseDir;

    /**
     * The output directory
     */
    @Parameter
    protected File outputJavaDir;

    /**
     * Component DSL Pom file
     */
    protected File componentDslPom;

    /**
     * Components DSL Metadata
     */
    @Parameter
    protected File outputResourcesDir;

    /**
     * The package where to generate component factories
     */
    @Parameter(defaultValue = "org.apache.camel.builder.component")
    protected String endpointFactoriesPackageName;

    /**
     * The package where to generate component DSL specific factories
     */
    @Parameter(defaultValue = "org.apache.camel.builder.component.dsl")
    protected String componentsDslFactoriesPackageName;

    /**
     * Generate or not the EndpointBuilderFactory interface.
     */
    @Parameter(defaultValue = "true")
    protected Boolean generateEndpointBuilderFactory;

    /**
     * Generate or not the EndpointBuilders interface.
     */
    @Parameter(defaultValue = "true")
    protected Boolean generateEndpointBuilders;

    DynamicClassLoader projectClassLoader;

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
        if(componentDslPom == null) {
            componentDslPom = findCamelDirectory(baseDir, "core/camel-componentdsl").toPath().resolve("pom.xml").toFile();
        }

        Map<File, Supplier<String>> files = PackageHelper.findJsonFiles(buildDir, p -> p.isDirectory() || p.getName().endsWith(".json")).values().stream()
                .collect(Collectors.toMap(Function.identity(), s -> cache(() -> loadJson(s))));

        executeComponent(files);

    }

    private static String loadJson(File file) {
        try {
            return loadText(file);
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    private static <T> Supplier<T> cache(Supplier<T> supplier) {
        return new Supplier<T>() {
            T value;

            @Override
            public T get() {
                if (value == null) {
                    value = supplier.get();
                }
                return value;
            }
        };
    }

    private static String loadComponentJson(Map<File, Supplier<String>> jsonFiles, String componentName) {
        return loadJsonOfType(jsonFiles, componentName, "component");
    }

    private static String loadJsonOfType(Map<File, Supplier<String>> jsonFiles, String modelName, String type) {
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
                    ComponentModel model = ComponentModel.generateComponentModelFromJsonString(json);
                    allModels.add(model);
                }
            }

            // Group the models by implementing classes
            Map<String, List<ComponentModel>> grModels = allModels.stream().collect(Collectors.groupingBy(ComponentModel::getJavaType));
            for (String componentClass : grModels.keySet()) {
                List<ComponentModel> compModels = grModels.get(componentClass);
                ComponentModel model = compModels.get(0); // They should be
                // equivalent
                List<String> aliases = compModels.stream().map(ComponentModel::getScheme).sorted().collect(Collectors.toList());

                String overrideComponentName = null;
                if (aliases.size() > 1) {
                    // determine component name when there are multiple ones
                    overrideComponentName = model.getArtifactId().replace("camel-", "");
                }
                createComponentDsl(model);
                //updatePomFile(componentDslPom, model);
            }
        }
    }

    private void createComponentDsl(final ComponentModel model) throws MojoExecutionException {
        final ComponentDslGenerator componentDslGenerator = ComponentDslGenerator.createDslJavaClassFromComponentModel(model, projectClassLoader);
        Path target = outputJavaDir.toPath().resolve(componentsDslFactoriesPackageName.replace('.', '/')).resolve(componentDslGenerator.getGeneratedClassName() + ".java");
        updateResource(buildContext, target, componentDslGenerator.printClassAsString());

        final ComponentDslMetadataGenerator componentDslMetadataGenerator = new ComponentDslMetadataGenerator(outputJavaDir.toPath().resolve(componentsDslFactoriesPackageName.replace('.', '/')).toFile(), outputResourcesDir.toPath().resolve("metadata.json").toFile());
        componentDslMetadataGenerator.addComponentToMetadataAndSyncMetadataFile(model, componentDslGenerator.getGeneratedClassName());

        syncPomFile(componentDslPom,componentDslMetadataGenerator.getComponentCacheFromMemory());
    }

    private void findComponentNames(File dir, Set<String> componentNames) {
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

    private void syncPomFile(final File pomFile, final Map<String, ComponentModel> componentsModels) throws MojoExecutionException {
        final String startMainComponentImportMarker = "<!-- START: camel components import -->";
        final String endMainComponentImportMarker = "<!-- END: camel components import -->";

        if (!pomFile.exists()) {
            throw new MojoExecutionException("Pom file " + pomFile.getPath() + " does not exist");
        }

        try {
            final String pomText = loadText(pomFile);

            final String before = Strings.before(pomText, startMainComponentImportMarker).trim();
            final String after = Strings.after(pomText, endMainComponentImportMarker).trim();

            final StringBuilder stringBuilder = new StringBuilder();
            componentsModels.forEach((key, model) -> {
                stringBuilder.append(generateDependencyModule(model));
            });
            final String updatedPom = before + "\n\t\t" + startMainComponentImportMarker + "\n" + stringBuilder.toString() + "\t\t"
                    + endMainComponentImportMarker + "\n\t\t" + after;
            updateResource(buildContext, componentDslPom.toPath(), updatedPom);
        } catch (IOException e) {
            throw new MojoExecutionException("Error reading file " + pomFile + " Reason: " + e, e);
        }
    }

    private String generateDependencyModule(final ComponentModel model) {
        return  "\t\t<dependency>\n" +
                "\t\t\t<groupId>" + model.getGroupId() + "</groupId>\n" +
                "\t\t\t<artifactId>" + model.getArtifactId() + "</artifactId>\n" +
                "\t\t\t<scope>provided</scope>\n" +
                "\t\t\t<version>${project.version}</version>\n" +
                "\t\t</dependency>\n";
    }
}
