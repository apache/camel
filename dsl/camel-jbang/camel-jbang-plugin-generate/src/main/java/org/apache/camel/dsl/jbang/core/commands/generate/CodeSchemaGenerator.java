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
import java.nio.file.Paths;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.main.download.DependencyDownloaderClassLoader;
import org.apache.camel.main.download.MavenDependencyDownloader;
import org.apache.camel.tooling.maven.MavenArtifact;
import picocli.CommandLine;

/**
 * Command to generate JSON Schema for Camel components and Java objects.
 *
 * This command downloads the specified Camel component dependencies and generates a JSON Schema (Draft 2020-12) for the
 * given fully qualified class name.
 *
 * Usage Examples: - camel generate schema fhir org.hl7.fhir.r4.model.Patient - camel generate schema http
 * org.apache.camel.component.http.HttpConfiguration
 *
 * The generated schema includes: - Public and non-public fields (with getters) - Flattened enums using toString() -
 * Jackson annotations support
 *
 * Exit Codes: - 0: Success - 1: Component not found or dependency download failed - 2: Class not found - 3: Schema
 * generation failed - 4: General error
 */
@CommandLine.Command(name = "schema",
                     description = "Create a JSON schema for a given Camel component and Java Object")
public class CodeSchemaGenerator extends CamelCommand {

    @CommandLine.Parameters(description = "Camel component name (e.g., 'fhir', 'http', 'jms')", arity = "1")
    private String camelComponent;

    @CommandLine.Parameters(description = "Fully qualified class name (e.g., 'org.hl7.fhir.r4.model.Patient')", arity = "1")
    private String fullyQualifiedName;

    @CommandLine.Option(names = { "--camel-version" },
                        description = "Camel version to use")
    private String camelVersion;

    @CommandLine.Option(names = { "--output" },
                        description = "Output file path (default: stdout)")
    private String outputFile;

    @CommandLine.Option(names = { "--verbose" },
                        description = "Enable verbose logging")
    private boolean verbose;

    @CommandLine.Option(names = { "--download" }, defaultValue = "true",
                        description = "Whether to allow automatic downloading JAR dependencies (over the internet)")
    boolean download = true;

    @CommandLine.Option(names = { "--repos" },
                        description = "Additional maven repositories (Use commas to separate multiple repositories)")
    String repositories;

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
                printer().println("Generating JSON Schema for component: " + camelComponent);
                printer().println("Class: " + fullyQualifiedName);
                printer().println("Camel version: " + camelVersion);
            }

            // Validate inputs
            if (camelComponent == null || camelComponent.trim().isEmpty()) {
                printer().printErr("Error: Camel component name cannot be empty");
                return 4;
            }

            if (fullyQualifiedName == null || fullyQualifiedName.trim().isEmpty()) {
                printer().printErr("Error: Fully qualified class name cannot be empty");
                return 4;
            }

            // Configure JSON Schema generator
            JacksonModule jacksonModule = new JacksonModule();
            SchemaGeneratorConfig config = createSchemaGeneratorConfig(jacksonModule);
            SchemaGenerator generator = new SchemaGenerator(config);

            // Set context class loader
            ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
            // Download dependencies and create class loader
            try (DependencyDownloaderClassLoader cl
                    = getDependencyDownloaderClassLoader(camelComponent, camelVersion, verbose)) {
                if (cl == null) {
                    return 1; // Error already reported
                }
                Thread.currentThread().setContextClassLoader(cl);

                // Load the target class
                Class<?> targetClass = loadTargetClass(cl, fullyQualifiedName);
                if (targetClass == null) {
                    return 2; // Error already reported
                }

                // Generate schema
                JsonNode schema = generateSchema(generator, targetClass);
                if (schema == null) {
                    return 3; // Error already reported
                }

                // Output schema
                outputSchema(schema);

                if (verbose) {
                    printer().println("Schema generation completed successfully");
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
            return 4;
        }
    }

    /**
     * Creates and configures the JSON Schema generator configuration.
     */
    private SchemaGeneratorConfig createSchemaGeneratorConfig(JacksonModule jacksonModule) {
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
    private DependencyDownloaderClassLoader getDependencyDownloaderClassLoader(
            String camelComponent, String version, boolean verbose) {

        try {
            if (verbose) {
                printer().println("Creating dependency downloader class loader...");
            }

            DependencyDownloaderClassLoader cl = new DependencyDownloaderClassLoader(
                    CodeSchemaGenerator.class.getClassLoader());

            try (MavenDependencyDownloader downloader = new MavenDependencyDownloader()) {
                downloader.setClassLoader(cl);
                downloader.setDownload(download);
                downloader.setRepositories(repositories);
                downloader.start();

                if (verbose) {
                    printer().println("Downloading artifacts for camel-" + camelComponent + ":" + version);
                }

                List<MavenArtifact> artifacts = downloader.downloadArtifacts(
                        "org.apache.camel",
                        "camel-" + camelComponent,
                        version,
                        true);

                if (artifacts == null || artifacts.isEmpty()) {
                    printer().printErr(
                            "Error: No artifacts found for component 'camel-" + camelComponent + "' version '" + version + "'");
                    printer().printErr("Please verify that the component name is correct and the version exists.");
                    printer().printErr("Available components can be found at: https://camel.apache.org/components/");
                    return null;
                }

                if (verbose) {
                    printer().println("Downloaded " + artifacts.size() + " artifact(s)");
                    artifacts.forEach(artifact -> printer().println("  - " + artifact.getFile().getName()));
                }

                artifacts.forEach(artifact -> cl.addFile(artifact.getFile()));
            }

            return cl;

        } catch (Exception e) {
            printer().printErr("Error downloading dependencies for component 'camel-" + camelComponent + "':");
            printer().printErr("  " + e.getMessage());
            printer().printErr(System.lineSeparator());
            printer().printErr("Possible causes:");
            printer().printErr("  - Component name is incorrect (check spelling)");
            printer().printErr("  - Version '" + version + "' does not exist");
            printer().printErr("  - Network connectivity issues");
            printer().printErr("  - Maven repository is unavailable");
            printer().printErr(System.lineSeparator());
            printer().printErr("You can find available components at: https://camel.apache.org/components/");

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
                printer().println("Loading class: " + className);
            }

            return classLoader.loadClass(className);

        } catch (ClassNotFoundException e) {

            printer().printErr("Error: Class '" + className + "' not found in component dependencies");
            printer().printErr(System.lineSeparator());
            printer().printErr("Possible causes:");
            printer().printErr("  - Class name is incorrect (check spelling and package)");
            printer().printErr("  - Class is not part of the '" + camelComponent + "' component");
            printer().printErr("  - Class is in a different artifact not included as dependency");
            printer().printErr(System.lineSeparator());
            printer().printErr("To find available classes, you can:");
            printer().printErr("  - Check the component documentation");
            printer().printErr("  - Browse the Maven artifact at: https://mvnrepository.com/artifact/org/apache/camel/camel-"
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
                printer().println("Generating schema for class: " + targetClass.getName());
            }

            return generator.generateSchema(targetClass);

        } catch (Exception e) {
            printer().printErr("Error generating schema for class '" + targetClass.getName() + "':");
            printer().printErr("  " + e.getMessage());
            printer().printErr(System.lineSeparator());
            printer().printErr("This might be caused by:");
            printer().printErr("  - Complex class structure that cannot be serialized");
            printer().printErr("  - Missing dependencies for the class");
            printer().printErr("  - Circular references in the class hierarchy");

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
        if (outputFile == null || outputFile.isEmpty()) {
            outputFile = Paths.get(".") + File.separator + fullyQualifiedName + "-schema.json";
        }

        ObjectMapper objectMapper = new ObjectMapper();

        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(new File(outputFile), schema);

        printer().println("Schema saved to: " + outputFile);
    }
}
