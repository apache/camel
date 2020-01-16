package org.apache.camel.maven.packaging;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.camel.maven.packaging.model.ComponentModel;
import org.apache.camel.maven.packaging.model.ComponentOptionModel;
import org.apache.camel.maven.packaging.model.EndpointOptionModel;
import org.apache.camel.tooling.util.JSonSchemaHelper;
import org.apache.camel.tooling.util.PackageHelper;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import static org.apache.camel.tooling.util.JSonSchemaHelper.getSafeValue;
import static org.apache.camel.tooling.util.PackageHelper.findCamelDirectory;
import static org.apache.camel.tooling.util.PackageHelper.loadText;

/**
 * Generate Endpoint DSL source files for Components.
 */
@Mojo(name = "generate-component-dsl", threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class ComponentDslMojo extends AbstractMojo {

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
    protected File outputDir;

    /**
     * Component DSL Pom file
     */
    @Parameter(defaultValue = "core/camel-componentdsl/pom.xml")
    protected File componentDslPom;

    /**
     * The package where to generate component Endpoint factories
     */
    @Parameter(defaultValue = "org.apache.camel.builder.endpoint")
    protected String endpointFactoriesPackageName;

    /**
     * The package where to generate component specific Endpoint factories
     */
    @Parameter(defaultValue = "org.apache.camel.builder.endpoint.dsl")
    protected String componentsFactoriesPackageName;

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

        if (outputDir == null) {
            outputDir = findCamelDirectory(baseDir, "core/camel-componentdsl/src/main/java");
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
                    ComponentModel model = generateComponentModel(componentName, json);
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

                updatePomFile(componentDslPom, model);
            }
        }
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

    private static ComponentModel generateComponentModel(String componentName, String json) {
        List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("component", json, false);

        ComponentModel component = new ComponentModel();
        component.setScheme(getSafeValue("scheme", rows));
        component.setSyntax(getSafeValue("syntax", rows));
        component.setAlternativeSyntax(getSafeValue("alternativeSyntax", rows));
        component.setTitle(getSafeValue("title", rows));
        component.setDescription(getSafeValue("description", rows));
        component.setFirstVersion(JSonSchemaHelper.getSafeValue("firstVersion", rows));
        component.setLabel(getSafeValue("label", rows));
        component.setDeprecated(getSafeValue("deprecated", rows));
        component.setDeprecationNote(getSafeValue("deprecationNote", rows));
        component.setConsumerOnly(getSafeValue("consumerOnly", rows));
        component.setProducerOnly(getSafeValue("producerOnly", rows));
        component.setJavaType(getSafeValue("javaType", rows));
        component.setGroupId(getSafeValue("groupId", rows));
        component.setArtifactId(getSafeValue("artifactId", rows));
        component.setVersion(getSafeValue("version", rows));

        rows = JSonSchemaHelper.parseJsonSchema("componentProperties", json, true);
        for (Map<String, String> row : rows) {
            ComponentOptionModel option = new ComponentOptionModel();
            option.setName(getSafeValue("name", row));
            option.setDisplayName(getSafeValue("displayName", row));
            option.setKind(getSafeValue("kind", row));
            option.setType(getSafeValue("type", row));
            option.setJavaType(getSafeValue("javaType", row));
            option.setDeprecated(getSafeValue("deprecated", row));
            option.setDeprecationNote(getSafeValue("deprecationNote", row));
            option.setDescription(getSafeValue("description", row));
            option.setDefaultValue(getSafeValue("defaultValue", row));
            option.setEnums(getSafeValue("enum", row));
            component.addComponentOption(option);
        }

        rows = JSonSchemaHelper.parseJsonSchema("properties", json, true);
        for (Map<String, String> row : rows) {
            EndpointOptionModel option = new EndpointOptionModel();
            option.setName(getSafeValue("name", row));
            option.setDisplayName(getSafeValue("displayName", row));
            option.setKind(getSafeValue("kind", row));
            option.setGroup(getSafeValue("group", row));
            option.setLabel(getSafeValue("label", row));
            option.setRequired(getSafeValue("required", row));
            option.setType(getSafeValue("type", row));
            option.setJavaType(getSafeValue("javaType", row));
            option.setEnums(getSafeValue("enum", row));
            option.setPrefix(getSafeValue("prefix", row));
            option.setMultiValue(getSafeValue("multiValue", row));
            option.setDeprecated(getSafeValue("deprecated", row));
            option.setDeprecationNote(getSafeValue("deprecationNote", row));
            option.setDefaultValue(getSafeValue("defaultValue", row));
            option.setDescription(getSafeValue("description", row));
            option.setEnumValues(getSafeValue("enum", row));
            component.addEndpointOption(option);
        }

        return component;
    }

    private boolean updatePomFile(final File file, final ComponentModel componentModel) throws MojoExecutionException {
        final String componentArtifactId = componentModel.getArtifactId();
        final String before = "<!-- START: " + componentArtifactId + " -->";
        final String after = "<!-- END: " + componentArtifactId + " -->";

        if (!file.exists()) {
            return false;
        }

        try {
            final String pomText = loadText(file);
            System.out.println(pomText);
        } catch (IOException e) {
            throw new MojoExecutionException("Error reading file " + file + " Reason: " + e, e);
        }
        return true;
    }

}
