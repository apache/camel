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

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.GistHelper;
import org.apache.camel.dsl.jbang.core.common.GitHubHelper;
import org.apache.camel.dsl.jbang.core.common.JSonHelper;
import org.apache.camel.dsl.jbang.core.common.Printer;
import org.apache.camel.github.GistResourceResolver;
import org.apache.camel.github.GitHubResourceResolver;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultResourceResolvers;
import org.apache.camel.main.KameletMain;
import org.apache.camel.main.download.DownloadListener;
import org.apache.camel.spi.ResourceResolver;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.v1.Integration;
import org.apache.camel.v1.IntegrationSpec;
import org.apache.camel.v1.integrationspec.Flows;
import org.apache.camel.v1.integrationspec.IntegrationKit;
import org.apache.camel.v1.integrationspec.Sources;
import org.apache.camel.v1.integrationspec.Template;
import org.apache.camel.v1.integrationspec.Traits;
import org.apache.camel.v1.integrationspec.template.Spec;
import org.apache.camel.v1.integrationspec.traits.Builder;
import org.apache.camel.v1.integrationspec.traits.Camel;
import org.apache.camel.v1.integrationspec.traits.Container;
import org.apache.camel.v1.integrationspec.traits.Environment;
import org.apache.camel.v1.integrationspec.traits.Mount;
import org.apache.camel.v1.integrationspec.traits.Openapi;
import org.apache.camel.v1.integrationspec.traits.ServiceBinding;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "run", description = "Run Camel integrations on Kubernetes", sortOptions = false)
public class IntegrationRun extends KubeBaseCommand {

    // ignored list of dependencies, can be either groupId or artifactId
    // as camel-k loads dependencies from the catalog produced by camel-k-runtime, some camel core dependencies
    // are not available, so we have to skip them
    private static final String[] SKIP_DEPS = new String[] { "camel-core-languages", "camel-endpointdsl" };

    @CommandLine.Parameters(description = "The Camel file(s) to run.",
                            arity = "0..9", paramLabel = "<files>")
    String[] filePaths;

    @CommandLine.Option(names = { "--name" },
                        description = "The integration name. Use this when the name should not get derived from the source file name.")
    String name;

    @CommandLine.Option(names = { "--image" },
                        description = "An image built externally (for instance via CI/CD). Enabling it will skip the integration build phase.")
    String image;

    @CommandLine.Option(names = { "--kit", "-k" }, description = "The kit used to run the integration.")
    String kit;

    @CommandLine.Option(names = { "--profile" }, description = "The trait profile to use for the deployment.")
    String profile;

    @CommandLine.Option(names = { "--service-account" }, description = "The service account used to run this Integration.")
    String serviceAccount;

    @CommandLine.Option(names = { "--pod-template" },
                        description = "The path of the YAML file containing a PodSpec template to be used for the integration pods.")
    String podTemplate;

    @CommandLine.Option(names = { "--operator-id", "-x" }, defaultValue = "camel-k",
                        description = "Operator id selected to manage this integration.")
    String operatorId = "camel-k";

    @CommandLine.Option(names = { "--dependency", "-d" },
                        description = "Adds dependency that should be included, use \"camel:\" prefix for a Camel component, \"mvn:org.my:app:1.0\" for a Maven dependency.")
    String[] dependencies;

    @CommandLine.Option(names = { "--property", "-p" },
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

    @CommandLine.Option(names = { "--env", "-e" },
                        description = "Set an environment variable in the integration container, for instance \"-e MY_VAR=my-value\".")
    String[] envVars;

    @CommandLine.Option(names = { "--volume", "-v" },
                        description = "Mount a volume into the integration container, for instance \"-v pvcname:/container/path\".")
    String[] volumes;

    @CommandLine.Option(names = { "--connect", "-c" },
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

    @CommandLine.Option(names = { "--traits", "-t" },
                        description = "Add a label to the integration. Use name values pairs like \"--label my.company=hello\".")
    String[] traits;

    @CommandLine.Option(names = { "--use-flows" }, defaultValue = "true",
                        description = "Write yaml sources as Flow objects in the integration custom resource.")
    boolean useFlows = true;

    @CommandLine.Option(names = { "--compression" },
                        description = "Enable storage of sources and resources as a compressed binary blobs.")
    boolean compression;

    @CommandLine.Option(names = { "--wait", "-w" }, description = "Wait for the integration to become ready.")
    boolean wait;

    @CommandLine.Option(names = { "--logs", "-l" }, description = "Print logs after integration has been started.")
    boolean logs;

    @CommandLine.Option(names = { "--output", "-o" },
                        description = "Just output the generated integration custom resource (supports: yaml or json).")
    String output;

    public IntegrationRun(CamelJBangMain main) {
        super(main);
        Arrays.sort(SKIP_DEPS);
    }

    public Integer doCall() throws Exception {
        List<String> integrationSources
                = Stream.concat(Arrays.stream(Optional.ofNullable(filePaths).orElseGet(() -> new String[] {})),
                        Arrays.stream(Optional.ofNullable(sources).orElseGet(() -> new String[] {}))).toList();

        Integration integration = new Integration();
        integration.setSpec(new IntegrationSpec());
        integration.getMetadata()
                .setName(getIntegrationName(integrationSources));

        if (kit != null) {
            IntegrationKit integrationKit = new IntegrationKit();
            integrationKit.setName(kit);
            integration.getSpec().setIntegrationKit(integrationKit);
        }

        if (profile != null) {
            TraitProfile p = TraitProfile.valueOf(profile.toUpperCase(Locale.US));
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

        if (operatorId != null) {
            if (integration.getMetadata().getAnnotations() == null) {
                integration.getMetadata().setAnnotations(new HashMap<>());
            }

            integration.getMetadata().getAnnotations().put(KubeCommand.OPERATOR_ID_LABEL, operatorId);
        }

        if (labels != null && labels.length > 0) {
            integration.getMetadata().setLabels(Arrays.stream(labels)
                    .filter(it -> it.contains("="))
                    .map(it -> it.split("="))
                    .filter(it -> it.length == 2)
                    .collect(Collectors.toMap(it -> it[0].trim(), it -> it[1].trim())));
        }

        Traits traitsSpec;
        if (traits != null && traits.length > 0) {
            traitsSpec = TraitHelper.parseTraits(traits);
        } else {
            traitsSpec = new Traits();
        }

        if (image != null) {
            Container containerTrait = new Container();
            containerTrait.setImage(image);
            traitsSpec.setContainer(containerTrait);
        } else {
            List<Source> resolvedSources = resolveSources(integrationSources);

            Set<String> intDependencies = calculateDependencies(resolvedSources);
            if (dependencies != null && dependencies.length > 0) {
                for (String dependency : dependencies) {
                    String normalized = normalizeDependency(dependency);
                    validateDependency(normalized, printer());
                    intDependencies.add(normalized);
                }
            }
            List<String> deps = new ArrayList<>(intDependencies);
            integration.getSpec().setDependencies(deps);

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
            Source templateSource = resolveSource(podTemplate);
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
                case "yaml" -> printer().println(KubernetesHelper.yaml().dumpAsMap(integration));
                case "json" -> printer().println(
                        JSonHelper.prettyPrint(KubernetesHelper.json().writer().writeValueAsString(integration), 2));
                default -> printer().printf("Unsupported output format %s%n", output);
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
            new IntegrationLogs(getMain()).watchLogs(integration);
        }

        return 0;
    }

    private void convertOptionsToTraits(Traits traitsSpec) {
        Mount mountTrait = null;

        if (configs != null && configs.length > 0) {
            mountTrait = new Mount();
            mountTrait.setConfigs(List.of(configs));
        }

        if (resources != null && resources.length > 0) {
            if (mountTrait == null) {
                mountTrait = new Mount();
            }
            mountTrait.setResources(List.of(resources));
        }

        if (volumes != null && volumes.length > 0) {
            if (mountTrait == null) {
                mountTrait = new Mount();
            }
            mountTrait.setVolumes(List.of(volumes));
        }

        if (mountTrait != null) {
            traitsSpec.setMount(mountTrait);
        }

        if (openApis != null && openApis.length > 0) {
            Openapi openapiTrait = new Openapi();
            openapiTrait.setConfigmaps(List.of(openApis));
            traitsSpec.setOpenapi(openapiTrait);
        }

        if (properties != null && properties.length > 0) {
            Camel camelTrait = new Camel();
            camelTrait.setProperties(List.of(properties));
            traitsSpec.setCamel(camelTrait);
        }

        if (buildProperties != null && buildProperties.length > 0) {
            Builder builderTrait = new Builder();
            builderTrait.setProperties(List.of(buildProperties));
            traitsSpec.setBuilder(builderTrait);
        }

        if (envVars != null && envVars.length > 0) {
            Environment environmentTrait = new Environment();
            environmentTrait.setVars(List.of(envVars));
            traitsSpec.setEnvironment(environmentTrait);
        }

        if (connects != null && connects.length > 0) {
            ServiceBinding serviceBindingTrait = new ServiceBinding();
            serviceBindingTrait.setServices(List.of(connects));
            traitsSpec.setServiceBinding(serviceBindingTrait);
        }
    }

    private Source resolveSource(String source) {
        List<Source> resolved = resolveSources(Collections.singletonList(source));
        if (resolved.isEmpty()) {
            throw new RuntimeCamelException("Failed to resolve source file: " + source);
        } else {
            return resolved.get(0);
        }
    }

    private List<Source> resolveSources(List<String> sourcePaths) {
        List<Source> resolved = new ArrayList<>();
        for (String sourcePath : sourcePaths) {
            SourceScheme sourceScheme = SourceScheme.fromUri(sourcePath);
            String fileExtension = FileUtil.onlyExt(sourcePath);
            String fileName = SourceScheme.onlyName(FileUtil.onlyName(sourcePath)) + "." + fileExtension;
            try {
                switch (sourceScheme) {
                    case GIST -> {
                        StringJoiner all = new StringJoiner(",");
                        GistHelper.fetchGistUrls(sourcePath, all);

                        try (ResourceResolver resolver = new GistResourceResolver()) {
                            for (String uri : all.toString().split(",")) {
                                resolved.add(new Source(
                                        fileName, sourcePath,
                                        IOHelper.loadText(resolver.resolve(uri).getInputStream()),
                                        fileExtension, compression, false));
                            }
                        }
                    }
                    case HTTP -> {
                        try (ResourceResolver resolver = new DefaultResourceResolvers.HttpResolver()) {
                            resolved.add(new Source(
                                    fileName, sourcePath,
                                    IOHelper.loadText(resolver.resolve(sourcePath).getInputStream()),
                                    fileExtension, compression, false));
                        }
                    }
                    case HTTPS -> {
                        try (ResourceResolver resolver = new DefaultResourceResolvers.HttpsResolver()) {
                            resolved.add(new Source(
                                    fileName, sourcePath,
                                    IOHelper.loadText(resolver.resolve(sourcePath).getInputStream()),
                                    fileExtension, compression, false));
                        }
                    }
                    case FILE -> {
                        try (ResourceResolver resolver = new DefaultResourceResolvers.FileResolver()) {
                            resolved.add(new Source(
                                    fileName, sourcePath,
                                    IOHelper.loadText(resolver.resolve(sourcePath).getInputStream()),
                                    fileExtension, compression, true));
                        }
                    }
                    case CLASSPATH -> {
                        try (ResourceResolver resolver = new DefaultResourceResolvers.ClasspathResolver()) {
                            resolver.setCamelContext(new DefaultCamelContext());
                            resolved.add(new Source(
                                    fileName, sourcePath,
                                    IOHelper.loadText(resolver.resolve(sourcePath).getInputStream()),
                                    fileExtension, compression, true));
                        }
                    }
                    case GITHUB, RAW_GITHUB -> {
                        StringJoiner all = new StringJoiner(",");
                        GitHubHelper.fetchGithubUrls(sourcePath, all);

                        try (ResourceResolver resolver = new GitHubResourceResolver()) {
                            for (String uri : all.toString().split(",")) {
                                resolved.add(new Source(
                                        fileName, sourcePath,
                                        IOHelper.loadText(resolver.resolve(uri).getInputStream()),
                                        fileExtension, compression, false));
                            }
                        }
                    }
                    case UNKNOWN -> {
                        try (FileInputStream fis = new FileInputStream(sourcePath)) {
                            resolved.add(
                                    new Source(fileName, sourcePath, IOHelper.loadText(fis), fileExtension, compression, true));
                        }
                    }
                }
            } catch (Exception e) {
                throw new RuntimeCamelException("Failed to resolve sources", e);
            }
        }
        return resolved;
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

    private record Source(String name, String path, String content, String extension, boolean compressed, boolean local) {

        /**
         * Provides source contant and automatically handles compression of content when enabled.
         *
         * @return the content, maybe compressed.
         */
        public String content() {
            if (compressed()) {
                return CompressionHelper.compressBase64(content);
            }

            return content;
        }

        public String language() {
            if ("yml".equals(extension)) {
                return "yaml";
            }

            return extension;
        }

        public boolean isYaml() {
            return "yaml".equals(language());
        }
    }

    private Set<String> calculateDependencies(List<Source> resolvedSources) throws Exception {
        List<String> files = new ArrayList<>();
        for (Source s : resolvedSources) {
            if (s.local && !s.path.startsWith("classpath:")) {
                // get the absolute path for a local file
                files.add("file://" + new File(s.path).getAbsolutePath());
            } else {
                files.add(s.path);
            }
        }
        final KameletMain main = new KameletMain();
        //        main.setDownload(false);
        main.setFresh(false);
        RunDownloadListener downloadListener = new RunDownloadListener(resolvedSources);
        main.setDownloadListener(downloadListener);
        main.setSilent(true);
        // enable stub in silent mode so we do not use real components
        main.setStubPattern("*");
        // do not run for very long in silent run
        main.addInitialProperty("camel.main.autoStartup", "false");
        main.addInitialProperty("camel.main.durationMaxSeconds", "1");
        main.addInitialProperty("camel.jbang.verbose", "false");
        main.addInitialProperty("camel.main.routesIncludePattern", String.join(",", files));

        main.start();
        main.run();

        main.stop();
        main.shutdown();
        return downloadListener.getDependencies();
    }

    private static class RunDownloadListener implements DownloadListener {

        final Set<String> dependencies = new TreeSet<>();
        private final List<Source> resolvedSources;

        public RunDownloadListener(List<Source> resolvedSources) {
            this.resolvedSources = resolvedSources;
        }

        @Override
        public void onDownloadDependency(String groupId, String artifactId, String version) {
            if (!skipArtifact(groupId, artifactId)) {
                // format: camel:<component name>
                // KameletMain is used to resolve the dependencies and it already contains
                // camel-kamelets and camel-rest artifacts, then the source code must be inspected
                // to actually add them if they are used in the route.
                if ("camel-rest".equals(artifactId) && routeContainsEndpoint("rest")) {
                    dependencies.add("camel:" + artifactId.replace("camel-", ""));
                }
                if (("camel-kamelet".equals(artifactId) || "camel-yaml-dsl".equals(artifactId))
                        && routeContainsEndpoint("kamelet")) {
                    dependencies.add("camel:" + artifactId.replace("camel-", ""));
                }
                if (!"camel-rest".equals(artifactId) && !"camel-kamelet".equals(artifactId)
                        && !"camel-yaml-dsl".equals(artifactId)) {
                    dependencies.add("camel:" + artifactId.replace("camel-", ""));
                }
            }
        }

        private boolean skipArtifact(String groupId, String artifactId) {
            return Arrays.binarySearch(SKIP_DEPS, artifactId) >= 0 || Arrays.binarySearch(SKIP_DEPS, groupId) >= 0;
        }

        // inspect the source code to determine if it contains a specific endpoint
        private boolean routeContainsEndpoint(String componentName) {
            boolean contains = false;
            for (Source source : resolvedSources) {
                // find if the route contains the component with the format: <component>:
                if (source.content.contains(componentName + ":")) {
                    contains = true;
                    break;
                }
            }
            return contains;
        }

        @Override
        public void onAlreadyDownloadedDependency(String groupId, String artifactId, String version) {
        }

        private Set<String> getDependencies() {
            return dependencies;
        }
    }

}
