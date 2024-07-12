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

package org.apache.camel.dsl.jbang.core.commands.kubernetes;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.Export;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.ContainerTrait;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.TraitCatalog;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.TraitContext;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.TraitHelper;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.TraitProfile;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.util.StringHelper;
import org.apache.camel.v1.integrationspec.Traits;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "export", description = "Export as Maven/Gradle project that contains a Kubernetes deployment manifest",
         sortOptions = false)
class KubernetesExport extends Export {

    @CommandLine.Option(names = { "--trait-profile" }, description = "The trait profile to use for the deployment.")
    String traitProfile;

    @CommandLine.Option(names = { "--property" },
                        description = "Add a runtime property or properties file from a path, a config map or a secret (syntax: [my-key=my-value|file:/path/to/my-conf.properties|[configmap|secret]:name]).")
    String[] properties;

    @CommandLine.Option(names = { "--config" },
                        description = "Add a runtime configuration from a ConfigMap or a Secret (syntax: [configmap|secret]:name[/key], where name represents the configmap/secret name and key optionally represents the configmap/secret key to be filtered).")
    String[] configs;

    @CommandLine.Option(names = { "--resource" },
                        description = "Add a runtime resource from a Configmap or a Secret (syntax: [configmap|secret]:name[/key][@path], where name represents the configmap/secret name, key optionally represents the configmap/secret key to be filtered and path represents the destination path).")
    String[] resources;

    @CommandLine.Option(names = { "--open-api-spec" }, description = "Add an OpenAPI spec (syntax: [configmap|file]:name).")
    String[] openApis;

    @CommandLine.Option(names = { "--env" },
                        description = "Set an environment variable in the integration container, for instance \"-e MY_VAR=my-value\".")
    String[] envVars;

    @CommandLine.Option(names = { "--volume" },
                        description = "Mount a volume into the integration container, for instance \"-v pvcname:/container/path\".")
    String[] volumes;

    @CommandLine.Option(names = { "--connect" },
                        description = "A Service that the integration should bind to, specified as [[apigroup/]version:]kind:[namespace/]name.")
    String[] connects;

    @CommandLine.Option(names = { "--annotation" },
                        description = "Add an annotation to the integration. Use name values pairs like \"--annotation my.company=hello\".")
    String[] annotations;

    @CommandLine.Option(names = { "--label" },
                        description = "Add a label to the integration. Use name values pairs like \"--label my.company=hello\".")
    String[] labels;

    @CommandLine.Option(names = { "--trait" },
                        description = "Add a trait configuration to the integration. Use name values pairs like \"--trait trait.name.config=hello\".")
    String[] traits;

    @CommandLine.Option(names = { "--image" },
                        description = "The image name to be built.")
    String image;

    @CommandLine.Option(names = { "--image-registry" },
                        defaultValue = "quay.io",
                        description = "The image registry to hold the app container image.")
    String imageRegistry = "quay.io";

    @CommandLine.Option(names = { "--image-group" },
                        description = "The image registry group used to push images to.")
    String imageGroup;

    public KubernetesExport(CamelJBangMain main) {
        super(main);
    }

    public KubernetesExport(CamelJBangMain main, RuntimeType runtime, String[] files, String exportDir, boolean quiet) {
        super(main);

        this.runtime = runtime;
        this.files = Arrays.asList(files);
        this.exportDir = exportDir;
        this.quiet = quiet;
    }

    public KubernetesExport(CamelJBangMain main, ExportConfigurer configurer) {
        super(main);

        runtime = configurer.runtime;
        quarkusVersion = configurer.quarkusVersion;
        symbolicLink = configurer.symbolicLink;
        mavenWrapper = configurer.mavenWrapper;
        gradleWrapper = configurer.gradleWrapper;
        exportDir = configurer.exportDir;
        files = configurer.files;
        gav = configurer.gav;
        fresh = configurer.fresh;
        download = configurer.download;
        quiet = configurer.quiet;
        logging = configurer.logging;
        loggingLevel = configurer.loggingLevel;
    }

    public Integer export() throws Exception {
        if (runtime == null) {
            runtime = RuntimeType.quarkus;
        }

        Map<String, String> exportProps = new HashMap<>();
        String resolvedImageRegistry = resolveImageRegistry();

        // TODO: remove when fixed kubernetes-client version is part of the Quarkus platform
        // pin kubernetes-client to this version because of https://github.com/fabric8io/kubernetes-client/issues/6059
        addDependencies("camel:cli-connector", "io.fabric8:kubernetes-client:6.13.1");

        String resolvedImageGroup = null;
        if (image != null) {
            resolvedImageGroup = extractImageGroup(image);
        } else if (imageGroup != null) {
            resolvedImageGroup = imageGroup;
        }

        if (runtime == RuntimeType.quarkus) {

            // Quarkus specific dependencies
            addDependencies("io.quarkus:quarkus-kubernetes");

            // Mutually exclusive image build plugins - use Jib by default
            if (!getDependenciesList().contains("io.quarkus:quarkus-container-image-docker")) {
                addDependencies("io.quarkus:quarkus-container-image-jib");
            }

            // Quarkus specific properties
            exportProps.put("quarkus.container-image.build", "true");

            if (resolvedImageRegistry != null) {
                exportProps.put("quarkus.container-image.registry", resolvedImageRegistry);
                if (resolvedImageRegistry.startsWith("localhost")) {
                    exportProps.put("quarkus.container-image.insecure", "true");
                }
            }

            if (resolvedImageGroup != null) {
                exportProps.put("quarkus.container-image.group", resolvedImageGroup);
            }
        } else {
            printer().println("Kubernetes export for runtime '" + runtime + "' not supported");
        }

        additionalProperties = Optional.ofNullable(additionalProperties).orElse("");
        if (additionalProperties.isEmpty()) {
            additionalProperties = exportProps.entrySet().stream()
                    .map(entry -> "%s=%s".formatted(entry.getKey(), entry.getValue())).collect(Collectors.joining(","));
        } else {
            additionalProperties += "," + exportProps.entrySet().stream()
                    .map(entry -> "%s=%s".formatted(entry.getKey(), entry.getValue())).collect(Collectors.joining(","));
        }

        String projectName = getProjectName();
        TraitContext context = new TraitContext(projectName, getVersion());
        if (annotations != null) {
            context.addAnnotations(Arrays.stream(annotations)
                    .map(item -> item.split("="))
                    .filter(parts -> parts.length == 2)
                    .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1])));
        }

        if (labels != null) {
            context.addLabels(Arrays.stream(labels)
                    .map(item -> item.split("="))
                    .filter(parts -> parts.length == 2)
                    .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1])));
        }

        if (traitProfile != null) {
            context.setProfile(TraitProfile.valueOf(traitProfile));
        }

        Traits traitsSpec;
        if (traits != null && traits.length > 0) {
            traitsSpec = TraitHelper.parseTraits(traits);
        } else {
            traitsSpec = new Traits();
        }

        TraitHelper.configureMountTrait(traitsSpec, configs, resources, volumes);
        TraitHelper.configureOpenApiSpec(traitsSpec, openApis);
        TraitHelper.configureProperties(traitsSpec, properties);
        TraitHelper.configureContainerImage(traitsSpec, image, resolvedImageRegistry, resolvedImageGroup, projectName,
                getVersion());
        TraitHelper.configureEnvVars(traitsSpec, envVars);
        TraitHelper.configureConnects(traitsSpec, connects);

        // Need to set quarkus.container properties, otherwise these settings get overwritten by Quarkus
        if (RuntimeType.quarkus == runtime && traitsSpec.getContainer() != null) {
            if (traitsSpec.getContainer().getName() != null && !traitsSpec.getContainer().getName().equals(projectName)) {
                additionalProperties += ",quarkus.kubernetes.container-name=%s".formatted(traitsSpec.getContainer().getName());
            }

            if (traitsSpec.getContainer().getPort() != null) {
                additionalProperties += ",quarkus.kubernetes.ports.%s.container-port=%s"
                        .formatted(Optional.ofNullable(traitsSpec.getContainer().getPortName())
                                .orElse(ContainerTrait.DEFAULT_CONTAINER_PORT_NAME), traitsSpec.getContainer().getPort());
            }

            if (traitsSpec.getContainer().getImagePullPolicy() != null) {
                additionalProperties += ",quarkus.kubernetes.image-pull-policy=%s"
                        .formatted(StringHelper.camelCaseToDash(traitsSpec.getContainer().getImagePullPolicy().getValue()));
            }
        }

        // run export
        int exit = super.export();
        if (exit != 0) {
            if (!quiet) {
                printer().println("Project export failed");
            }
            return exit;
        }

        if (!quiet) {
            printer().println("Building Kubernetes manifest ...");
        }

        if (traitProfile != null) {
            new TraitCatalog().traitsForProfile(TraitProfile.valueOf(traitProfile.toUpperCase(Locale.US))).forEach(t -> {
                if (t.configure(traitsSpec, context)) {
                    t.apply(traitsSpec, context);
                }
            });
        } else {
            new TraitCatalog().allTraits().forEach(t -> {
                if (t.configure(traitsSpec, context)) {
                    t.apply(traitsSpec, context);
                }
            });
        }

        String yaml = context.buildItems().stream().map(KubernetesHelper::dumpYaml).collect(Collectors.joining("---\n"));
        safeCopy(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)),
                new File(exportDir + "/src/main/kubernetes/kubernetes.yml"));

        if (!quiet) {
            printer().println("Project export successful!");
        }

        return 0;
    }

    private String resolveImageRegistry() {
        String resolvedImageRegistry = null;
        if (image != null) {
            resolvedImageRegistry = extractImageRegistry(image);
        } else if (imageRegistry != null) {
            if (imageRegistry.equals("kind") || imageRegistry.equals("kind-registry")) {
                resolvedImageRegistry = "localhost:5001";
            } else if (imageRegistry.equals("minikube") || imageRegistry.equals("minikube-registry")) {
                resolvedImageRegistry = "localhost:5000";
            } else {
                resolvedImageRegistry = imageRegistry;
            }
        }
        return resolvedImageRegistry;
    }

    private String extractImageGroup(String image) {
        String[] parts = image.split("/");
        if (parts.length == 3) {
            return parts[1];
        } else if (parts.length > 1) {
            return parts[0];
        }

        return imageGroup;
    }

    private String extractImageRegistry(String image) {
        String[] parts = image.split("/");
        if (parts.length == 3) {
            return parts[0];
        }

        return imageRegistry;
    }

    protected String getProjectName() {
        if (image != null) {
            return KubernetesHelper.sanitize(KubernetesHelper.sanitize(StringHelper.beforeLast(image, ":")));
        }

        return KubernetesHelper.sanitize(super.getProjectName());
    }

    protected String getVersion() {
        if (image != null) {
            return StringHelper.afterLast(image, ":");
        }

        return super.getVersion();
    }

    /**
     * Configurer used to customize internal options for the Export command.
     */
    record ExportConfigurer(RuntimeType runtime,
            String quarkusVersion,
            boolean symbolicLink,
            boolean mavenWrapper,
            boolean gradleWrapper,
            String exportDir,
            List<String> files,
            String gav,
            boolean fresh,
            boolean download,
            boolean quiet,
            boolean logging,
            String loggingLevel) {
    }
}
