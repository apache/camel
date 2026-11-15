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
package org.apache.camel.dsl.jbang.core.commands.generate;

import java.io.File;
import java.util.List;

import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonSchemaModule;
import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Mixin;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.MavenResolverMixin;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.main.download.DependencyDownloaderClassLoader;
import org.apache.camel.main.download.MavenDependencyDownloader;
import org.apache.camel.tooling.maven.MavenArtifact;
import org.apache.camel.util.IOHelper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Command to generate JSON Schema for Camel components and Java objects.
 *
 * This command downloads the specified Camel component dependencies and generates a JSON Schema (Draft 2020-12) for the
 * given fully qualified class name.
 *
 * Usage Examples: - camel generate schema fhir org.hl7.fhir.r4.model.Patient - camel generate schema timer
 * org.apache.camel.component.timer.TimerComponent
 *
 * The generated schema includes: - Public and non-public fields (with getters) - Flattened enums using toString() -
 * Jackson annotations support
 *
 * The schema goes to stdout, or to the file of --output; --verbose tells on stderr what the command does.
 *
 * Exit Codes: - 0: Success - 1: the dependencies cannot be downloaded, or the schema cannot be generated - 2: a
 * component or class name that is empty - 3: the class is not in the dependencies of the component - 70: an unexpected
 * error
 */
@CommandDefinition(name = "schema", description = "Create a JSON schema for a given Camel component and Java Object",
                   generateHelp = true, sortOptions = true)
public class CodeSchemaGenerator extends CamelCommand {

    @Argument(index = "0", required = true, description = "Camel component name (e.g., 'fhir', 'http', 'jms')")
    private String camelComponent;

    @Argument(index = "1", required = true, description = "Fully qualified class name (e.g., 'org.hl7.fhir.r4.model.Patient')")
    private String fullyQualifiedName;

    @org.aesh.command.option.Option(name = "camel-version", description = "Camel version to use")
    private String camelVersion;

    @org.aesh.command.option.Option(name = "output", description = "Output file path (default: stdout)")
    private String outputFile;

    @org.aesh.command.option.Option(name = "verbose", hasValue = false, description = "Enable verbose logging")
    private boolean verbose;

    @Mixin
    MavenResolverMixin mavenResolver;

    public CodeSchemaGenerator(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        try {
            if (camelVersion == null) {
                camelVersion = new DefaultCamelCatalog().getCatalogVersion();
            }

            if (verbose) {
                printer().printDiagnostic("Generating JSON Schema for component: " + camelComponent);
                printer().printDiagnostic("Class: " + fullyQualifiedName);
                printer().printDiagnostic("Camel version: " + camelVersion);
            }

            // Validate inputs
            if (camelComponent == null || camelComponent.trim().isEmpty()) {
                printer().printErr("Camel component name cannot be empty");
                return CliExitCode.USAGE;
            }

            if (fullyQualifiedName == null || fullyQualifiedName.trim().isEmpty()) {
                printer().printErr("Fully qualified class name cannot be empty");
                return CliExitCode.USAGE;
            }

            // Configure JSON Schema generator
            JacksonSchemaModule jacksonModule = new JacksonSchemaModule();
            SchemaGeneratorConfig config = createSchemaGeneratorConfig(jacksonModule);
            SchemaGenerator generator = new SchemaGenerator(config);

            // Set context class loader
            ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
            // Download dependencies and create class loader
            try (DependencyDownloaderClassLoader cl
                    = getDependencyDownloaderClassLoader(camelComponent, camelVersion, verbose)) {
                if (cl == null) {
                    return CliExitCode.FAILURE; // Error already reported
                }
                Thread.currentThread().setContextClassLoader(cl);

                // Load the target class
                Class<?> targetClass = loadTargetClass(cl, fullyQualifiedName);
                if (targetClass == null) {
                    return CliExitCode.NOT_FOUND; // Error already reported
                }

                // Generate schema
                JsonNode schema = generateSchema(generator, targetClass);
                if (schema == null) {
                    return CliExitCode.FAILURE; // Error already reported
                }

                // Output schema
                outputSchema(schema);

                if (verbose) {
                    printer().printDiagnostic("Schema generation completed successfully");
                }

                return 0;

            } finally {
                Thread.currentThread().setContextClassLoader(originalClassLoader);
            }

        } catch (Exception e) {
            printer().printErr("Unexpected error during schema generation: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return CliExitCode.INTERNAL;
        }
    }

    /**
     * Creates and configures the JSON Schema generator configuration.
     */
    private SchemaGeneratorConfig createSchemaGeneratorConfig(JacksonSchemaModule jacksonModule) {
        return new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12)
                .with(jacksonModule)
                .with(Option.FLATTENED_ENUMS_FROM_TOSTRING)
                .without(Option.FIELDS_DERIVED_FROM_ARGUMENTFREE_METHODS)
                .without(Option.NONSTATIC_NONVOID_NONGETTER_METHODS)
                .without(Option.NULLABLE_METHOD_RETURN_VALUES_BY_DEFAULT)
                .without(Option.VOID_METHODS)
                .without(Option.GETTER_METHODS)
                .without(Option.FIELDS_DERIVED_FROM_ARGUMENTFREE_METHODS)
                .without(Option.STATIC_METHODS)
                .build();
    }

    /**
     * Downloads dependencies and creates a class loader for the specified Camel component. NOTE: Make sure to close the
     * stream after using it!
     *
     * @param  camelComponent The name of the Camel component
     * @param  version        The Camel version to use
     * @param  verbose        Whether to enable verbose logging
     * @return                DependencyDownloaderClassLoader or null if failed
     */
    DependencyDownloaderClassLoader getDependencyDownloaderClassLoader(
            String camelComponent, String version, boolean verbose) {

        try {
            if (verbose) {
                printer().printDiagnostic("Creating dependency downloader class loader...");
            }

            DependencyDownloaderClassLoader cl = new DependencyDownloaderClassLoader(
                    CodeSchemaGenerator.class.getClassLoader());

            try (MavenDependencyDownloader downloader = new MavenDependencyDownloader()) {
                downloader.setClassLoader(cl);
                downloader.setDownload(mavenResolver.download());
                downloader.setRepositories(mavenResolver.repos());
                downloader.start();

                if (verbose) {
                    printer().printDiagnostic("Downloading artifacts for camel-" + camelComponent + ":" + version);
                }

                List<MavenArtifact> artifacts = downloader.downloadArtifacts(
                        "org.apache.camel",
                        "camel-" + camelComponent,
                        version,
                        true);

                if (artifacts == null || artifacts.isEmpty()) {
                    printer().printErr(
                            "Error: No artifacts found for component 'camel-" + camelComponent + "' version '" + version + "'");
                    printer().printDiagnostic("Please verify that the component name is correct and the version exists.");
                    printer().printDiagnostic("Available components can be found at: https://camel.apache.org/components/");
                    IOHelper.close(cl);
                    return null;
                }

                if (verbose) {
                    printer().printDiagnostic("Downloaded " + artifacts.size() + " artifact(s)");
                    artifacts.forEach(artifact -> printer().printDiagnostic("  - " + artifact.getFile().getName()));
                }

                artifacts.forEach(artifact -> cl.addFile(artifact.getFile()));
            } catch (Exception e) {
                IOHelper.close(cl);
                throw e;
            }

            return cl;

        } catch (Exception e) {
            printer().printErr("Error downloading dependencies for component 'camel-" + camelComponent + "':");
            printer().printDiagnostic("  " + e.getMessage());
            printer().printDiagnostic("");
            printer().printDiagnostic("Possible causes:");
            printer().printDiagnostic("  - Component name is incorrect (check spelling)");
            printer().printDiagnostic("  - Version '" + version + "' does not exist");
            printer().printDiagnostic("  - Network connectivity issues");
            printer().printDiagnostic("  - Maven repository is unavailable");
            printer().printDiagnostic("");
            printer().printDiagnostic("You can find available components at: https://camel.apache.org/components/");

            if (verbose) {
                e.printStackTrace();
            }
            return null;
        }
    }

    /**
     * Loads the target class using the provided class loader.
     *
     * @param  classLoader The class loader to use
     * @param  className   The fully qualified class name
     * @return             The loaded Class or null if failed
     */
    private Class<?> loadTargetClass(ClassLoader classLoader, String className) {
        try {
            if (verbose) {
                printer().printDiagnostic("Loading class: " + className);
            }

            return classLoader.loadClass(className);

        } catch (ClassNotFoundException e) {

            printer().printErr("Class '" + className + "' not found in component dependencies");
            printer().printDiagnostic("");
            printer().printDiagnostic("Possible causes:");
            printer().printDiagnostic("  - Class name is incorrect (check spelling and package)");
            printer().printDiagnostic("  - Class is not part of the '" + camelComponent + "' component");
            printer().printDiagnostic("  - Class is in a different artifact not included as dependency");
            printer().printDiagnostic("");
            printer().printDiagnostic("To find available classes, you can:");
            printer().printDiagnostic("  - Check the component documentation");
            printer().printDiagnostic(
                    "  - Browse the Maven artifact at: https://mvnrepository.com/artifact/org/apache/camel/camel-"
                                      + camelComponent);

            if (verbose) {
                e.printStackTrace();
            }
            return null;

        } catch (Exception e) {
            printer().printErr("Error loading class '" + className + "': " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return null;
        }
    }

    /**
     * Generates the JSON schema for the given class.
     *
     * @param  generator   The schema generator
     * @param  targetClass The class to generate schema for
     * @return             The generated schema or null if failed
     */
    private JsonNode generateSchema(SchemaGenerator generator, Class<?> targetClass) {
        try {
            if (verbose) {
                printer().printDiagnostic("Generating schema for class: " + targetClass.getName());
            }

            return generator.generateSchema(targetClass);

        } catch (Exception e) {
            printer().printErr("Error generating schema for class '" + targetClass.getName() + "':");
            printer().printDiagnostic("  " + e.getMessage());
            printer().printDiagnostic("");
            printer().printDiagnostic("This might be caused by:");
            printer().printDiagnostic("  - Complex class structure that cannot be serialized");
            printer().printDiagnostic("  - Missing dependencies for the class");
            printer().printDiagnostic("  - Circular references in the class hierarchy");

            if (verbose) {
                e.printStackTrace();
            }
            return null;
        }
    }

    /**
     * Outputs the generated schema file.
     *
     * @param schema The schema to output
     */
    private void outputSchema(JsonNode schema) throws Exception {
        ObjectMapper objectMapper = JsonMapper.builderWithJackson2Defaults().build();

        if (outputFile == null || outputFile.isEmpty()) {
            printer().println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(schema));
        } else {
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(new File(outputFile), schema);
            printer().println("Schema saved to: " + outputFile);
        }
    }
}
