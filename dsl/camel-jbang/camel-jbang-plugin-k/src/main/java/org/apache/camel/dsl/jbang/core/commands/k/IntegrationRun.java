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
package org.apache.camel.dsl.jbang.core.commands.k;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.KubernetesBaseCommand;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.KubernetesHelper;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.TraitCatalog;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.TraitContext;
import org.apache.camel.dsl.jbang.core.common.JSonHelper;
import org.apache.camel.dsl.jbang.core.common.Printer;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.dsl.jbang.core.common.Source;
import org.apache.camel.dsl.jbang.core.common.SourceHelper;
import org.apache.camel.dsl.jbang.core.common.SourceScheme;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.v1.Integration;
import org.apache.camel.v1.IntegrationSpec;
import org.apache.camel.v1.integrationspec.Flows;
import org.apache.camel.v1.integrationspec.IntegrationKit;
import org.apache.camel.v1.integrationspec.Sources;
import org.apache.camel.v1.integrationspec.Template;
import org.apache.camel.v1.integrationspec.Traits;
import org.apache.camel.v1.integrationspec.template.Spec;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "run", description = "Run Camel integrations on Kubernetes", sortOptions = false)
public class IntegrationRun extends KubernetesBaseCommand {

    @CommandLine.Parameters(description = "The Camel file(s) to run.",
                            arity = "0..9", paramLabel = "<files>")
    String[] filePaths;

    @CommandLine.Option(names = { "--name" },
                        description = "The integration name. Use this when the name should not get derived from the source file name.")
    String name;

    @CommandLine.Option(names = { "--image" },
                        description = "An image built externally (for instance via CI/CD). Enabling it will skip the integration build phase.")
    String image;

    @CommandLine.Option(names = { "--kit" }, description = "The kit used to run the integration.")
    String kit;

    @CommandLine.Option(names = { "--trait-profile" }, description = "The trait profile to use for the deployment.")
    String traitProfile;

    @CommandLine.Option(names = { "--integration-profile" }, description = "The integration profile to use for the deployment.")
    String integrationProfile;

    @CommandLine.Option(names = { "--service-account" }, description = "The service account used to run this Integration.")
    String serviceAccount;

    @CommandLine.Option(names = { "--pod-template" },
                        description = "The path of the YAML file containing a PodSpec template to be used for the integration pods.")
    String podTemplate;

    @CommandLine.Option(names = { "--operator-id" }, defaultValue = "camel-k",
                        description = "Operator id selected to manage this integration.")
    String operatorId = "camel-k";

    @CommandLine.Option(names = { "--dependency" },
                        description = "Adds dependency that should be included, use \"camel:\" prefix for a Camel component, \"mvn:org.my:app:1.0\" for a Maven dependency.")
    String[] dependencies;

    @CommandLine.Option(names = { "--property" },
                        description = "Add a runtime property or properties file from a path, a config map or a secret (syntax: [my-key=my-value|file:/path/to/my-conf.properties|[configmap|secret]:name]).")
    String[] properties;

    @CommandLine.Option(names = { "--build-property" },
                        description = "Add a build time property or properties file from a path, a config map or a secret (syntax: [my-key=my-value|file:/path/to/my-conf.properties|[configmap|secret]:name]]).")
    String[] buildProperties;

    @CommandLine.Option(names = { "--config" },
                        description = "Add a runtime configuration from a ConfigMap or a Secret (syntax: [configmap|secret]:name[/key], where name represents the configmap/secret name and key optionally represents the configmap/secret key to be filtered).")
    String[] configs;

    @CommandLine.Option(names = { "--resource" },
                        description = "Add a runtime resource from a Configmap or a Secret (syntax: [configmap|secret]:name[/key][@path], where name represents the configmap/secret name, key optionally represents the configmap/secret key to be filtered and path represents the destination path).")
    String[] resources;

    @CommandLine.Option(names = { "--open-api" }, description = "Add an OpenAPI spec (syntax: [configmap|file]:name).")
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

    @CommandLine.Option(names = { "--source" },
                        description = "Add source file to your integration, this is added to the list of files listed as arguments of the command.")
    String[] sources;

    @CommandLine.Option(names = { "--maven-repository" }, description = "Add a maven repository used to resolve dependencies.")
    String[] repositories;

    @CommandLine.Option(names = { "--annotation" },
                        description = "Add an annotation to the integration. Use name values pairs like \"--annotation my.company=hello\".")
    String[] annotations;

    @CommandLine.Option(names = { "--label" },
                        description = "Add a label to the integration. Use name values pairs like \"--label my.company=hello\".")
    String[] labels;

    @CommandLine.Option(names = { "--trait" },
                        description = "Add a trait configuration to the integration. Use name values pairs like \"--trait trait.name.config=hello\".")
    String[] traits;

    @CommandLine.Option(names = { "--use-flows" }, defaultValue = "true",
                        description = "Write yaml sources as Flow objects in the integration custom resource.")
    boolean useFlows = true;

    @CommandLine.Option(names = { "--compression" },
                        description = "Enable storage of sources and resources as a compressed binary blobs.")
    boolean compression;

    @CommandLine.Option(names = { "--wait" }, description = "Wait for the integration to become ready.")
    boolean wait;

    @CommandLine.Option(names = { "--logs" }, description = "Print logs after integration has been started.")
    boolean logs;

    @CommandLine.Option(names = { "--output" },
                        description = "Just output the generated integration custom resource (supports: yaml or json).")
    String output;

    public IntegrationRun(CamelJBangMain main) {
        super(main);
    }

    public Integer doCall() throws Exception {
        // Operator id must be set
        if (ObjectHelper.isEmpty(operatorId)) {
            printer().println("Operator id must be set");
            return -1;
        }

        List<String> integrationSources
                = Stream.concat(Arrays.stream(Optional.ofNullable(filePaths).orElseGet(() -> new String[] {})),
                        Arrays.stream(Optional.ofNullable(sources).orElseGet(() -> new String[] {}))).toList();

        Integration integration = new Integration();
        integration.setSpec(new IntegrationSpec());
        integration.getMetadata()
                .setName(getIntegrationName(integrationSources));

        if (dependencies != null && dependencies.length > 0) {
            List<String> deps = new ArrayList<>();
            for (String dependency : dependencies) {
                String normalized = normalizeDependency(dependency);
                validateDependency(normalized, printer());
                deps.add(normalized);
            }

            integration.getSpec().setDependencies(deps);
        }

        if (kit != null) {
            IntegrationKit integrationKit = new IntegrationKit();
            integrationKit.setName(kit);
            integration.getSpec().setIntegrationKit(integrationKit);
        }

        if (traitProfile != null) {
            TraitProfile p = TraitProfile.valueOf(traitProfile.toUpperCase(Locale.US));
            integration.getSpec().setProfile(p.name().toLowerCase(Locale.US));
        }

        if (repositories != null && repositories.length > 0) {
            integration.getSpec().setRepositories(List.of(repositories));
        }

        if (annotations != null && annotations.length > 0) {
            integration.getMetadata().setAnnotations(Arrays.stream(annotations)
                    .filter(it -> it.contains("="))
                    .map(it -> it.split("="))
                    .filter(it -> it.length == 2)
                    .collect(Collectors.toMap(it -> it[0].trim(), it -> it[1].trim())));
        }

        if (integration.getMetadata().getAnnotations() == null) {
            integration.getMetadata().setAnnotations(new HashMap<>());
        }

        // --operator-id={id} is a syntax sugar for '--annotation camel.apache.org/operator.id={id}'
        integration.getMetadata().getAnnotations().put(CamelKCommand.OPERATOR_ID_LABEL, operatorId);

        // --integration-profile={id} is a syntax sugar for '--annotation camel.apache.org/integration-profile.id={id}'
        if (integrationProfile != null) {
            if (integrationProfile.contains("/")) {
                String[] namespacedName = integrationProfile.split("/", 2);
                integration.getMetadata().getAnnotations().put(CamelKCommand.INTEGRATION_PROFILE_NAMESPACE_ANNOTATION,
                        namespacedName[0]);
                integration.getMetadata().getAnnotations().put(CamelKCommand.INTEGRATION_PROFILE_ANNOTATION, namespacedName[1]);
            } else {
                integration.getMetadata().getAnnotations().put(CamelKCommand.INTEGRATION_PROFILE_ANNOTATION,
                        integrationProfile);
            }
        }

        if (labels != null && labels.length > 0) {
            integration.getMetadata().setLabels(Arrays.stream(labels)
                    .filter(it -> it.contains("="))
                    .map(it -> it.split("="))
                    .filter(it -> it.length == 2)
                    .collect(Collectors.toMap(it -> it[0].trim(), it -> it[1].trim())));
        }

        Traits traitsSpec = IntegrationTraitHelper.parseTraits(traits);

        if (image != null) {
            IntegrationTraitHelper.configureContainerImage(traitsSpec, image, null, null, null, null);
        } else {
            List<Source> resolvedSources = SourceHelper.resolveSources(integrationSources, compression);

            List<Flows> flows = new ArrayList<>();
            List<Sources> sources = new ArrayList<>();
            for (Source source : resolvedSources) {
                if (useFlows && source.isYaml() && !source.compressed()) {
                    JsonNode json = KubernetesHelper.json().convertValue(
                            KubernetesHelper.yaml().load(source.content()), JsonNode.class);
                    if (json.isArray()) {
                        for (JsonNode item : json) {
                            Flows flowSpec = new Flows();
                            flowSpec.setAdditionalProperties(KubernetesHelper.json().readerFor(Map.class).readValue(item));
                            flows.add(flowSpec);
                        }
                    } else {
                        Flows flowSpec = new Flows();
                        flowSpec.setAdditionalProperties(KubernetesHelper.json().readerFor(Map.class).readValue(json));
                        flows.add(flowSpec);
                    }
                } else {
                    Sources sourceSpec = new Sources();
                    sourceSpec.setName(source.name());
                    sourceSpec.setLanguage(source.language());
                    sourceSpec.setContent(source.content());
                    sourceSpec.setCompression(source.compressed());
                    sources.add(sourceSpec);
                }
            }

            if (!flows.isEmpty()) {
                integration.getSpec().setFlows(flows);
            }

            if (!sources.isEmpty()) {
                integration.getSpec().setSources(sources);
            }
        }

        if (podTemplate != null) {
            Source templateSource = SourceHelper.resolveSource(podTemplate);
            if (!templateSource.isYaml()) {
                throw new RuntimeCamelException(
                        ("Unsupported pod template %s - " +
                         "please use proper YAML source").formatted(templateSource.extension()));
            }

            Spec podSpec = KubernetesHelper.yaml().loadAs(templateSource.content(), Spec.class);
            Template template = new Template();
            template.setSpec(podSpec);
            integration.getSpec().setTemplate(template);
        }

        convertOptionsToTraits(traitsSpec);
        integration.getSpec().setTraits(traitsSpec);

        if (serviceAccount != null) {
            integration.getSpec().setServiceAccountName(serviceAccount);
        }

        if (output != null) {
            switch (output) {
                case "k8s" -> {
                    List<Source> sources = SourceHelper.resolveSources(integrationSources);
                    TraitContext context
                            = new TraitContext(integration.getMetadata().getName(), "1.0-SNAPSHOT", printer(), sources);
                    IntegrationTraitHelper.configureContainerImage(traitsSpec, image, "quay.io", null,
                            integration.getMetadata().getName(),
                            "1.0-SNAPSHOT");
                    org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Traits kubernetesTraits
                            = KubernetesHelper.yaml(this.getClass().getClassLoader())
                                    .loadAs(KubernetesHelper.dumpYaml(traits),
                                            org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Traits.class);
                    new TraitCatalog().apply(kubernetesTraits, context, traitProfile, RuntimeType.quarkus);

                    printer().println(
                            context.buildItems().stream().map(KubernetesHelper::dumpYaml).collect(Collectors.joining("---")));
                }
                case "yaml" -> printer().println(KubernetesHelper.dumpYaml(integration));
                case "json" -> printer().println(
                        JSonHelper.prettyPrint(KubernetesHelper.json().writer().writeValueAsString(integration), 2));
                default -> {
                    printer().printf("Unsupported output format '%s' (supported: yaml, json)%n", output);
                    return -1;
                }
            }

            return 0;
        }

        final AtomicBoolean updated = new AtomicBoolean(false);
        client(Integration.class).resource(integration).createOr(it -> {
            updated.set(true);
            return it.update();
        });

        if (updated.get()) {
            printer().printf("Integration %s updated%n", integration.getMetadata().getName());
        } else {
            printer().printf("Integration %s created%n", integration.getMetadata().getName());
        }

        if (wait || logs) {
            client(Integration.class).withName(integration.getMetadata().getName())
                    .waitUntilCondition(it -> "Running".equals(it.getStatus().getPhase()), 10, TimeUnit.MINUTES);
        }

        if (logs) {
            IntegrationLogs logsCommand = new IntegrationLogs(getMain());
            logsCommand.withClient(client());
            logsCommand.withName(integration.getMetadata().getName());
            logsCommand.doCall();
        }

        return 0;
    }

    private void convertOptionsToTraits(Traits traitsSpec) {
        IntegrationTraitHelper.configureMountTrait(traitsSpec, configs, resources, volumes);
        if (openApis != null) {
            Stream.of(openApis).forEach(openapi -> IntegrationTraitHelper.configureOpenApiSpec(traitsSpec, openapi));
        }
        IntegrationTraitHelper.configureProperties(traitsSpec, properties);
        IntegrationTraitHelper.configureBuildProperties(traitsSpec, buildProperties);
        IntegrationTraitHelper.configureEnvVars(traitsSpec, envVars);
        IntegrationTraitHelper.configureConnects(traitsSpec, connects);
    }

    private String getIntegrationName(List<String> sources) {
        if (name != null) {
            return KubernetesHelper.sanitize(name);
        } else if (image != null) {
            return KubernetesHelper.sanitize(image.replaceAll(":", "-v"));
        } else if (ObjectHelper.isNotEmpty(sources)) {
            return KubernetesHelper.sanitize(SourceScheme.onlyName(FileUtil.onlyName(sources.get(0))));
        }

        throw new RuntimeCamelException(
                "Failed to resolve integration name - please give an image, an explicit name option or a single source file");
    }

    /**
     * Normalize dependency expression. Basically replaces "camel-" based artifact names to use proper "camel:" prefix.
     *
     * @param  dependency to normalize.
     * @return            normalized dependency.
     */
    private static String normalizeDependency(String dependency) {
        if (dependency.startsWith("camel-quarkus-")) {
            return "camel:" + dependency.substring("camel-quarkus-".length());
        }

        if (dependency.startsWith("camel-quarkus:")) {
            return "camel:" + dependency.substring("camel-quarkus:".length());
        }

        if (dependency.startsWith("camel-k-")) {
            return "camel-k:" + dependency.substring("camel-k-".length());
        }

        if (dependency.startsWith("camel-")) {
            return "camel:" + dependency.substring("camel-".length());
        }

        return dependency;
    }

    /**
     * Validates given dependency expression.
     *
     * @param dependency to validate.
     * @param printer    to output potential warnings.
     */
    private static void validateDependency(String dependency, Printer printer) {
        if (dependency.startsWith("mvn:org.apache.camel:")) {
            String suggested = normalizeDependency(dependency.split(":")[2]);
            printer.printf("Warning: do not use '%s' as a dependency. Please use '%s' instead%n", dependency, suggested);
        }
        if (dependency.startsWith("mvn:org.apache.camel.quarkus:")) {
            String suggested = normalizeDependency(dependency.split(":")[2]);
            printer.printf("Warning: do not use '%s' as a dependency. Please use '%s' instead%n", dependency, suggested);
        }
    }

}
