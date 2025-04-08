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
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.Export;
import org.apache.camel.dsl.jbang.core.commands.ExportBaseCommand;
import org.apache.camel.dsl.jbang.core.commands.Run;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.TraitCatalog;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.TraitContext;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.TraitHelper;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Container;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Traits;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.dsl.jbang.core.common.RuntimeUtil;
import org.apache.camel.dsl.jbang.core.common.Source;
import org.apache.camel.dsl.jbang.core.common.SourceHelper;
import org.apache.camel.util.CamelCaseOrderedProperties;
import org.apache.camel.util.StringHelper;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "export", description = "Export as Maven/Gradle project that contains a Kubernetes deployment manifest",
         sortOptions = false)
public class KubernetesExport extends Export {

    @CommandLine.Option(names = { "--service-account" }, description = "The service account used to run the application.")
    protected String serviceAccount;

    @CommandLine.Option(names = { "--config" },
                        description = "Add a runtime configuration from a ConfigMap or a Secret (syntax: [configmap|secret]:name[/key], where name represents the configmap/secret name and key optionally represents the configmap/secret key to be filtered).")
    protected String[] configs;

    @CommandLine.Option(names = { "--resource" },
                        description = "Add a runtime resource from a Configmap or a Secret (syntax: [configmap|secret]:name[/key][@path], where name represents the configmap/secret name, key optionally represents the configmap/secret key to be filtered and path represents the destination path).")
    protected String[] resources;

    @CommandLine.Option(names = { "--env" },
                        description = "Set an environment variable in the integration container, for instance \"-e MY_VAR=my-value\".")
    protected String[] envVars;

    @CommandLine.Option(names = { "--volume" },
                        description = "Mount a volume into the integration container, for instance \"-v pvcname:/container/path\".")
    protected String[] volumes;

    @CommandLine.Option(names = { "--connect" },
                        description = "A Service that the integration should bind to, specified as [[apigroup/]version:]kind:[namespace/]name.")
    protected String[] connects;

    @CommandLine.Option(names = { "--annotation" },
                        description = "Add an annotation to the integration. Use name values pairs like \"--annotation my.company=hello\".")
    protected String[] annotations;

    @CommandLine.Option(names = { "--label" },
                        description = "Add a label to the integration. Use name values pairs like \"--label my.company=hello\".")
    protected String[] labels;

    @CommandLine.Option(names = { "--trait" },
                        description = "Add a trait configuration to the integration. Use name values pairs like \"--trait trait.name.config=hello\".")
    protected String[] traits;

    @CommandLine.Option(names = { "--image" },
                        description = "The image name to be built.")
    protected String image;

    @CommandLine.Option(names = { "--image-registry" },
                        description = "The image registry to hold the app container image.")
    protected String imageRegistry;

    @CommandLine.Option(names = { "--image-group" },
                        description = "The image registry group used to push images to.")
    protected String imageGroup;

    @CommandLine.Option(names = { "--image-builder" }, defaultValue = "jib",
                        description = "The image builder used to build the container image (e.g. docker, jib, podman).")
    protected String imageBuilder = "jib";

    @CommandLine.Option(names = { "--image-push" }, defaultValue = "true",
                        description = "Whether to push the container image to a given image registry.")
    protected boolean imagePush = true;

    @CommandLine.Option(names = { "--image-platform" },
                        description = "List of target platforms. Each platform is defined using os and architecture (e.g. linux/amd64).")
    protected String[] imagePlatforms;

    @CommandLine.Option(names = { "--base-image" },
                        description = "The base image that is used to build the container image from (default is eclipse-temurin:<java-version>).")
    protected String baseImage;

    @CommandLine.Option(names = { "--registry-mirror" },
                        description = "Optional Docker registry mirror where to pull images from when building the container image.")
    protected String registryMirror;

    @CommandLine.Option(names = { "--cluster-type" },
                        description = "The target cluster type. Special configurations may be applied to different cluster types such as Kind or Minikube or Openshift.")
    protected String clusterType;

    private static final String SRC_MAIN_RESOURCES = "/src/main/resources/";

    public KubernetesExport(CamelJBangMain main) {
        super(main);
    }

    public KubernetesExport(CamelJBangMain main, String[] files) {
        super(main);
        this.files = Arrays.asList(files);
    }

    public KubernetesExport(CamelJBangMain main, ExportConfigurer configurer) {
        super(main);

        runtime = configurer.runtime;
        quarkusVersion = configurer.quarkusVersion;

        files = configurer.files;
        name = configurer.name;
        gav = configurer.gav;
        repositories = configurer.repositories;
        dependencies = configurer.dependencies;
        excludes = configurer.excludes;
        mavenSettings = configurer.mavenSettings;
        mavenSettingsSecurity = configurer.mavenSettingsSecurity;
        mavenCentralEnabled = configurer.mavenCentralEnabled;
        mavenApacheSnapshotEnabled = configurer.mavenApacheSnapshotEnabled;
        javaVersion = configurer.javaVersion;
        camelVersion = configurer.camelVersion;
        kameletsVersion = configurer.kameletsVersion;
        profile = configurer.profile;
        localKameletDir = configurer.localKameletDir;
        springBootVersion = configurer.springBootVersion;
        camelSpringBootVersion = configurer.camelSpringBootVersion;
        quarkusGroupId = configurer.quarkusGroupId;
        quarkusArtifactId = configurer.quarkusArtifactId;
        buildTool = configurer.buildTool;
        openapi = configurer.openapi;
        exportDir = configurer.exportDir;
        packageName = configurer.packageName;
        buildProperties = configurer.buildProperties;
        symbolicLink = configurer.symbolicLink;
        javaLiveReload = configurer.javaLiveReload;
        ignoreLoadingError = configurer.ignoreLoadingError;
        mavenWrapper = configurer.mavenWrapper;
        gradleWrapper = configurer.gradleWrapper;
        fresh = configurer.fresh;
        download = configurer.download;
        quiet = configurer.quiet;
        logging = configurer.logging;
        loggingLevel = configurer.loggingLevel;
        verbose = configurer.verbose;
    }

    public Integer export() throws Exception {
        if (runtime == null) {
            runtime = RuntimeType.quarkus;
        }

        printer().println("Exporting application ...");

        if (!buildTool.equals("maven")) {
            printer().printf("--build-tool=%s is not yet supported%n", buildTool);
        }

        // Resolve image group and registry
        String resolvedImageGroup = resolveImageGroup();
        String resolvedImageRegistry = resolveImageRegistry();

        if (resolvedImageRegistry != null) {
            buildProperties.add("jkube.container-image.registry=%s".formatted(resolvedImageRegistry));
            if (imagePush) {
                buildProperties.add("jkube.docker.push.registry=%s".formatted(resolvedImageRegistry));
            }

            // [TODO] jkube config for insecure registries?
            var allowInsecure = resolvedImageRegistry.startsWith("localhost");
            if (allowInsecure && "jib".equals(imageBuilder)) {
                buildProperties.add("jib.allowInsecureRegistries=true");
            }
        }

        String projectName = getProjectName();
        CamelCatalog catalog = CatalogHelper.loadCatalog(runtime, runtime.version());

        List<Source> sources;
        try {
            sources = SourceHelper.resolveSources(files);
        } catch (Exception e) {
            printer().printf("Project export failed: %s - %s%n", e.getMessage(),
                    Optional.ofNullable(e.getCause()).map(Throwable::getMessage).orElse("unknown reason"));
            return 1;
        }

        TraitContext context = new TraitContext(projectName, getVersion(), printer(), catalog, sources);

        // Add annotations to TraitContext
        //
        annotations = Optional.ofNullable(annotations).orElse(new String[0]);
        context.addAnnotations(Arrays.stream(annotations)
                .map(item -> item.split("="))
                .filter(parts -> parts.length == 2)
                .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1])));

        // Add labels to TraitContext
        //
        // Generated by quarkus/jkube
        // app.kubernetes.io/name
        // app.kubernetes.io/version
        //
        addLabel("app.kubernetes.io/runtime", "camel");
        context.addLabels(Arrays.stream(labels)
                .map(item -> item.split("="))
                .filter(parts -> parts.length == 2)
                .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1])));

        if (clusterType != null) {
            context.setClusterType(ClusterType.valueOf(clusterType.toUpperCase()));
        }

        if (serviceAccount != null) {
            context.setServiceAccount(serviceAccount);
        }

        // application.properties
        String[] applicationProperties = extractPropertiesTraits(new File("application.properties"));

        // application-{profile}.properties
        String[] applicationProfileProperties = null;
        if (this.profile != null) {
            // override from profile specific configuration
            applicationProfileProperties = extractPropertiesTraits(new File("application-" + profile + ".properties"));
        }

        Traits traitsSpec = getTraitSpec(applicationProperties, applicationProfileProperties);

        TraitHelper.configureMountTrait(traitsSpec, configs, resources, volumes);
        if (openapi != null && openapi.startsWith("configmap:")) {
            TraitHelper.configureOpenApiSpec(traitsSpec, openapi);
            // Remove OpenAPI spec option to avoid duplicate handling by parent export command
            openapi = null;
        }
        TraitHelper.configureContainerImage(traitsSpec, image,
                resolvedImageRegistry, resolvedImageGroup, projectName, getVersion());
        TraitHelper.configureEnvVars(traitsSpec, envVars);
        TraitHelper.configureConnects(traitsSpec, connects);

        Container container = traitsSpec.getContainer();

        buildProperties.add("jkube.image.name=%s".formatted(container.getImage()));
        buildProperties.add("jkube.container-image.name=%s".formatted(container.getImage()));

        if (container.getName() != null && !container.getName().equals(projectName)) {
            printer().printf("Custom container name '%s' not supported%n".formatted(container.getName()));
        }

        if (container.getImagePullPolicy() != null) {
            var imagePullPolicy = container.getImagePullPolicy().getValue();
            buildProperties.add("jkube.container-image.imagePullPolicy=%s".formatted(imagePullPolicy));
        }

        buildProperties.add("jkube.skip.push=%b".formatted(!imagePush));

        if (ClusterType.OPENSHIFT.isEqualTo(clusterType)) {
            if (!"docker".equals(imageBuilder)) {
                printer().printf("OpenShift forcing --image-builder=docker%n");
                imageBuilder = "docker";
            }
            buildProperties.add("jkube.maven.plugin=%s".formatted("openshift-maven-plugin"));
        } else {
            buildProperties.add("jkube.maven.plugin=%s".formatted("kubernetes-maven-plugin"));
        }

        if (baseImage == null) {
            // use default base image with java version
            baseImage = "eclipse-temurin:%s".formatted(javaVersion);
        }

        if (registryMirror != null) {
            baseImage = "%s/%s".formatted(registryMirror, baseImage);
        }

        buildProperties.add("jkube.base.image=%s".formatted(baseImage));
        buildProperties.add("jkube.build.strategy=%s".formatted(imageBuilder));

        if ("jib".equals(imageBuilder)) {
            buildProperties.add("jkube.container-image.nocache=true");
            buildProperties.add("jib.disableUpdateChecks=true");
        }

        if (imagePlatforms != null) {
            buildProperties.add("jkube.container-image.platforms=%s".formatted(
                    Arrays.stream(imagePlatforms).distinct().collect(Collectors.joining(","))));
        }

        // Runtime specific for Main
        if (runtime == RuntimeType.main) {
            addDependencies("org.apache.camel:camel-health", "org.apache.camel:camel-platform-http-main");
        }

        File settings = new File(CommandLineHelper.getWorkDir(), Run.RUN_SETTINGS_FILE);
        var jkubeVersion = jkubeMavenPluginVersion(settings, mapBuildProperties());
        buildProperties.add("jkube.version=%s".formatted(jkubeVersion));

        // Run export
        int exit = super.doExport();
        if (exit != 0) {
            printer().println("Project export failed");
            return exit;
        }

        // Post export processing
        printer().println("Building Kubernetes manifest ...");

        new TraitCatalog().apply(traitsSpec, context, clusterType, runtime);

        // Dump each fragment to its respective kind
        var kubeFragments = context.buildItems().stream().map(KubernetesHelper::toJsonMap).toList();
        for (var map : kubeFragments) {
            var ymlFragment = KubernetesHelper.dumpYaml(map);
            var kind = map.get("kind").toString().toLowerCase();
            safeCopy(new ByteArrayInputStream(ymlFragment.getBytes(StandardCharsets.UTF_8)),
                    new File(exportDir + "/src/main/jkube/%s.yml".formatted(kind)));

        }

        context.doWithConfigurationResources((fileName, content) -> {
            try {
                File target = new File(exportDir + SRC_MAIN_RESOURCES + fileName);
                if (target.exists()) {
                    Files.writeString(target.toPath(), "%n%s".formatted(content), StandardOpenOption.APPEND);
                } else {
                    safeCopy(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), target);
                }
            } catch (Exception e) {
                printer().printf("Failed to create configuration resource %s - %s%n",
                        exportDir + SRC_MAIN_RESOURCES + fileName, e.getMessage());
            }
        });

        printer().println("Project export successful!");

        return 0;
    }

    protected Integer export(ExportBaseCommand cmd) throws Exception {
        if (runtime == RuntimeType.quarkus) {
            cmd.pomTemplateName = "quarkus-kubernetes-pom.tmpl";
        }
        if (runtime == RuntimeType.springBoot) {
            cmd.pomTemplateName = "spring-boot-kubernetes-pom.tmpl";
        }
        if (runtime == RuntimeType.main) {
            cmd.pomTemplateName = "main-kubernetes-pom.tmpl";
        }
        return super.export(cmd);
    }

    protected Traits getTraitSpec(String[] applicationProperties, String[] applicationProfileProperties) {

        var annotationsTraits = TraitHelper.extractTraitsFromAnnotations(this.annotations);
        var allTraits = TraitHelper.mergeTraits(traits, annotationsTraits, applicationProfileProperties, applicationProperties);

        Traits traitsSpec;
        if (allTraits.length > 0) {
            traitsSpec = TraitHelper.parseTraits(allTraits);
        } else {
            traitsSpec = new Traits();
        }

        return traitsSpec;
    }

    private void addLabel(String key, String value) {
        var labelArray = Optional.ofNullable(labels).orElse(new String[0]);
        var labelList = new ArrayList<>(Arrays.asList(labelArray));
        var labelEntry = "%s=%s".formatted(key, value);
        if (!labelList.contains(labelEntry)) {
            labelList.add(labelEntry);
            labels = labelList.toArray(new String[0]);
        }
    }

    private String resolveImageGroup() {
        if (image != null) {
            return extractImageGroup(image);
        }

        if (imageGroup != null) {
            return imageGroup;
        }

        if (gav != null) {
            var groupId = parseMavenGav(gav).getGroupId();
            var dotToks = groupId.split("\\.");
            return dotToks[dotToks.length - 1];
        }

        return null;
    }

    private String resolveImageRegistry() {
        if (image != null) {
            String extracted = extractImageRegistry(image);
            if (extracted != null) {
                return extracted;
            }
        }

        if (imageRegistry != null) {
            if (imageRegistry.equals("kind") || imageRegistry.equals("kind-registry")) {
                return "localhost:5001";
            } else if (imageRegistry.equals("minikube") || imageRegistry.equals("minikube-registry")) {
                return "localhost:5000";
            } else {
                return imageRegistry;
            }
        }

        if (ClusterType.KIND.isEqualTo(clusterType)) {
            return "localhost:5001";
        } else if (ClusterType.MINIKUBE.isEqualTo(clusterType)) {
            return "localhost:5000";
        }

        return null;
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

    protected String[] extractPropertiesTraits(File file) throws Exception {
        if (file.exists()) {
            Properties prop = new CamelCaseOrderedProperties();
            RuntimeUtil.loadProperties(prop, file);
            return TraitHelper.extractTraitsFromProperties(prop);
        } else {
            return null;
        }
    }

    protected String getProjectName() {
        if (name != null) {
            return KubernetesHelper.sanitize(name);
        }
        if (image != null) {
            return KubernetesHelper.sanitize(StringHelper.beforeLast(image, ":"));
        }
        return KubernetesHelper.sanitize(super.getProjectName());
    }

    protected String getVersion() {
        if (image != null) {
            return StringHelper.afterLast(image, ":");
        }

        return super.getVersion();
    }

    protected void setApplicationProperties(String[] props) {
        this.applicationProperties = props;
    }

    /**
     * Configurer used to customize internal options for the Export command.
     */
    public record ExportConfigurer(RuntimeType runtime,
            String quarkusVersion,
            List<String> files,
            String name,
            String gav,
            String repositories,
            List<String> dependencies,
            List<String> excludes,
            String mavenSettings,
            String mavenSettingsSecurity,
            boolean mavenCentralEnabled,
            boolean mavenApacheSnapshotEnabled,
            String javaVersion,
            String camelVersion,
            String kameletsVersion,
            String profile,
            String localKameletDir,
            String springBootVersion,
            String camelSpringBootVersion,
            String quarkusGroupId,
            String quarkusArtifactId,
            String buildTool,
            String openapi,
            String exportDir,
            String packageName,
            List<String> buildProperties,
            boolean symbolicLink,
            boolean javaLiveReload,
            boolean ignoreLoadingError,
            boolean mavenWrapper,
            boolean gradleWrapper,
            boolean fresh,
            boolean download,
            boolean quiet,
            boolean logging,
            String loggingLevel,
            boolean verbose) {
    }
}
