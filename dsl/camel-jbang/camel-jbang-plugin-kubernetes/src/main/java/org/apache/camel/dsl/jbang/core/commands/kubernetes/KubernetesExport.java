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
import java.nio.file.Path;
import java.nio.file.Paths;
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
import org.apache.camel.dsl.jbang.core.commands.ExportHelper;
import org.apache.camel.dsl.jbang.core.commands.Run;
import org.apache.camel.dsl.jbang.core.commands.RunHelper;
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
import org.apache.camel.util.FileUtil;
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
                        description = "The image builder used to build the container image (e.g. docker, jib, s2i).")
    protected String imageBuilder = "jib";

    @CommandLine.Option(names = { "--image-push" }, defaultValue = "true",
                        description = "Whether to push the container image to a given image registry.")
    protected boolean imagePush = true;

    @CommandLine.Option(names = { "--image-platform" },
                        description = "List of target platforms. Each platform is defined using os and architecture (e.g. linux/amd64).")
    protected String[] imagePlatforms;

    @CommandLine.Option(names = { "--base-image" },
                        description = "The base image that is used to build the container image from (default is mirror.gcr.io/library/eclipse-temurin:21-jdk:<java-version>).")
    protected String baseImage;

    @CommandLine.Option(names = { "--registry-mirror" },
                        description = "Optional Docker registry mirror where to pull images from when building the container image.")
    protected String registryMirror;

    @CommandLine.Option(names = { "--cluster-type" },
                        completionCandidates = ClusterTypeCompletionCandidates.class,
                        converter = ClusterTypeConverter.class,
                        description = "The target cluster type (${COMPLETION-CANDIDATES}). Special configurations may be applied to different cluster types such as Kind or Minikube.")
    protected String clusterType;

    private static final String SRC_MAIN_RESOURCES = "/src/main/resources/";

    public KubernetesExport(CamelJBangMain main) {
        super(main);
    }

    public KubernetesExport(CamelJBangMain main, String[] files) {
        super(main);
        this.files.addAll(Arrays.asList(files));
    }

    public KubernetesExport(CamelJBangMain main, ExportConfigurer configurer) {
        super(main);

        runtime = configurer.runtime;
        quarkusVersion = configurer.quarkusVersion;

        exportBaseDir = configurer.exportBaseDir;
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
        skipPlugins = configurer.skipPlugins;
        packageScanJars = configurer.packageScanJars;
        quiet = configurer.quiet;
        logging = configurer.logging;
        loggingLevel = configurer.loggingLevel;
        verbose = configurer.verbose;
        observe = true; // always include observability-services for kubernetes
    }

    public Integer export() throws Exception {
        if (runtime == null) {
            runtime = RuntimeType.quarkus;
        }

        // special if user type: camel run . or camel run dirName
        if (files != null && files.size() == 1) {
            String name = FileUtil.stripTrailingSeparator(files.get(0));
            Path first = Path.of(name);
            if (Files.isDirectory(first)) {
                exportBaseDir = first;
                RunHelper.dirToFiles(name, files);
            }
        }
        if (exportBaseDir == null) {
            exportBaseDir = Paths.get(".");
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
        String runtimeVersion;
        if (runtime == RuntimeType.quarkus) {
            runtimeVersion = quarkusVersion;
        } else if (runtime == RuntimeType.springBoot) {
            runtimeVersion = camelSpringBootVersion;
        } else {
            runtimeVersion = camelVersion;
        }
        CamelCatalog catalog = CatalogHelper.loadCatalog(runtime, runtimeVersion, download);

        List<Source> sources;
        try {
            addFile(Run.RUN_JAVA_SH);
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

        annotations = Optional.ofNullable(annotations).orElse(new String[0]);
        context.addAnnotations(Arrays.stream(annotations)
                .map(item -> item.split("="))
                .filter(parts -> parts.length == 2)
                .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1])));

        // Add labels to TraitContext
        //
        // Generated by jkube
        // app.kubernetes.io/name
        // app.kubernetes.io/version
        //
        context.addLabel("app.kubernetes.io/runtime", "camel");
        if (labels != null) {
            context.addLabels(Arrays.stream(labels)
                    .map(item -> item.split("="))
                    .filter(parts -> parts.length == 2)
                    .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1])));
        }

        if (clusterType != null) {
            context.setClusterType(ClusterType.valueOf(clusterType.toUpperCase()));
        }

        if (serviceAccount != null) {
            context.setServiceAccount(serviceAccount);
        }

        // application-{profile}.properties
        var applicationProfileProperties = new String[0];
        if (this.profile != null) {
            // override from profile specific configuration
            applicationProfileProperties
                    = extractPropertiesTraits(exportBaseDir.resolve("application-" + profile + ".properties"));
        } else {
            for (String f : files) {
                String name = FileUtil.stripPath(f);
                if ("application.properties".equals(name)) {
                    // load default properties configuration
                    applicationProfileProperties
                            = extractPropertiesTraits(exportBaseDir.resolve(f));
                }
            }
        }

        Traits traitsSpec = getTraitSpec(applicationProfileProperties, applicationProperties);

        // Map properties to env variables (where needed)
        var propsMap = propertiesMap(applicationProfileProperties, applicationProperties);
        if (propsMap.containsKey("ssl.truststore.certificates")) {
            addEnvVar("SSL_TRUSTSTORE_CERTIFICATES", propsMap.get("ssl.truststore.certificates"));
        }

        TraitHelper.configureMountTrait(traitsSpec, configs, resources, volumes);
        if (openapi != null && openapi.startsWith("configmap:")) {
            TraitHelper.configureOpenApiSpec(traitsSpec, openapi);
            // Remove OpenAPI spec option to avoid duplicate handling by parent export command
            openapi = null;
        }
        TraitHelper.configureContainerImage(traitsSpec, image,
                resolvedImageRegistry, resolvedImageGroup, projectName, getVersion(), buildProperties);
        TraitHelper.configureEnvVars(traitsSpec, envVars);
        TraitHelper.configureConnects(traitsSpec, connects);

        Container container = traitsSpec.getContainer();
        if (container.getName() != null && !container.getName().equals(projectName)) {
            printer().printf("Custom container name '%s' not supported%n".formatted(container.getName()));
        }

        buildProperties.add("jkube.skip.push=%b".formatted(!imagePush));

        if (ClusterType.OPENSHIFT.isEqualTo(clusterType)) {
            // Displays the Camel logo as part the deployment
            context.addLabel("app.openshift.io/runtime", "camel");

            if (!"docker".equals(imageBuilder)) {
                printer().printf("OpenShift forcing --image-builder=docker%n");
                imageBuilder = "docker";
            }
            // the deployment trait already generates the src/main/jkube/deployment.yml
            // but we also have to set in the jkube to generate Deployment instead of DeploymentConfig
            buildProperties.add("jkube.build.switchToDeployment=true");
            buildProperties.add("jkube.maven.plugin=%s".formatted("openshift-maven-plugin"));
        } else {
            buildProperties.add("jkube.maven.plugin=%s".formatted("kubernetes-maven-plugin"));
        }

        if (baseImage == null) {
            // use default base image with java version
            baseImage = "mirror.gcr.io/library/eclipse-temurin:%s".formatted(javaVersion);
        }

        if (registryMirror != null) {
            baseImage = "%s/%s".formatted(registryMirror, baseImage);
        }

        buildProperties.add("jkube.container-image.from=%s".formatted(baseImage));
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

        Path settingsPath = CommandLineHelper.getWorkDir().resolve(Run.RUN_SETTINGS_FILE);
        var jkubeVersion = jkubeMavenPluginVersion(settingsPath, mapBuildProperties());
        var managementPort = httpManagementPort(settingsPath);
        buildProperties.add("jkube.version=%s".formatted(jkubeVersion));

        boolean cronJobEnabled = traitsSpec.getCronjob() != null && traitsSpec.getCronjob().getEnabled();
        if (cronJobEnabled) {
            // set this property to allow the JVM to finish quickly once there are no more exchange messages
            // important for cronjobs so that the jvm can end quickly
            addToApplicationProperties(
                    "camel.main.duration-max-idle-seconds=" + traitsSpec.getCronjob().getDurationMaxIdleSeconds());
        } else {
            setContainerHealthPaths(managementPort);
        }

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
            ExportHelper.safeCopy(new ByteArrayInputStream(ymlFragment.getBytes(StandardCharsets.UTF_8)),
                    Paths.get(exportDir, "src/main/jkube", kind + ".yml"));

        }

        context.doWithConfigurationResources((fileName, content) -> {
            try {
                Path targetPath = Paths.get(exportDir, "src/main/resources", fileName);
                if (Files.exists(targetPath)) {
                    Files.writeString(targetPath, "%n%s".formatted(content), StandardOpenOption.APPEND);
                } else {
                    ExportHelper.safeCopy(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), targetPath);
                }
            } catch (Exception e) {
                printer().printf("Failed to create configuration resource %s - %s%n",
                        exportDir + SRC_MAIN_RESOURCES + fileName, e.getMessage());
            }
        });

        printer().println("Project export successful!");

        return 0;
    }

    protected Integer export(Path exportBaseDir, ExportBaseCommand cmd) throws Exception {
        if (runtime == RuntimeType.quarkus) {
            cmd.pomTemplateName = "quarkus-kubernetes-pom.tmpl";
        }
        if (runtime == RuntimeType.springBoot) {
            cmd.pomTemplateName = "spring-boot-kubernetes-pom.tmpl";
        }
        if (runtime == RuntimeType.main) {
            cmd.pomTemplateName = "main-kubernetes-pom.tmpl";
        }
        return super.export(exportBaseDir, cmd);
    }

    protected Traits getTraitSpec(String[] applicationProfileProperties, String[] applicationProperties) {

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

    private void addFile(String file) {
        if (!files.contains(file)) {
            // ensure mutability
            files = new ArrayList<>(files);
            files.add(file);
        }
    }

    private void addEnvVar(String key, String value) {
        var envArray = Optional.ofNullable(envVars).orElse(new String[0]);
        var envList = new ArrayList<>(Arrays.asList(envArray));
        var envEntry = "%s=%s".formatted(key, value);
        if (!envList.contains(envEntry)) {
            envList.add(envEntry);
            envVars = envList.toArray(new String[0]);
        }
    }

    private String resolveImageGroup() {
        if (image != null) {
            return extractImageGroup(image);
        }

        if (imageGroup != null) {
            return imageGroup;
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

    private void setContainerHealthPaths(int port) {
        // the camel-observability-services artifact is added as dependency if observe=true in run command
        // it renames the container health base path to /observe, so this has to be in the container health probes http path
        // only quarkus and sb runtimes, because there is no published health endpoints when using runtime=main
        String probePort = port > 0 ? "" + port : "9876";
        if (RuntimeType.quarkus == runtime) {
            // jkube reads quarkus properties to set the container health probes path
            buildProperties.add("jkube.enricher.jkube-healthcheck-quarkus.port=" + probePort);
            buildProperties.add("quarkus.smallrye-health.root-path=/observe/health");
            addToApplicationProperties("quarkus.management.port=" + probePort);
        } else if (RuntimeType.springBoot == runtime) {
            // addDependencies("org.springframework.boot:spring-boot-starter-actuator");
            // jkube reads spring-boot properties to set the kubernetes container health probes path
            // in this case, jkube reads from the application.properties and not from the build properties in pom.xml
            addToApplicationProperties("management.endpoints.web.base-path=/observe",
                    "management.server.port=" + probePort,
                    // jkube uses the old property to enable the readiness/liveness probes
                    // TODO: rename this property once https://github.com/eclipse-jkube/jkube/issues/3690 is fixed
                    "management.health.probes.enabled=true");
        } else if (RuntimeType.main == runtime) {
            addToApplicationProperties("camel.management.port=" + probePort);
        }
    }

    // helper method to add parameters to the applicationProperties
    // it takes care to resize the string array
    private void addToApplicationProperties(String... lines) {
        List<String> newProps = new ArrayList<>();
        for (String line : lines) {
            newProps.add(line);
        }
        if (applicationProperties != null) {
            newProps.addAll(Arrays.asList(applicationProperties));
        }
        applicationProperties = newProps.toArray(new String[newProps.size()]);
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

    protected String[] extractPropertiesTraits(Path path) throws Exception {
        if (Files.exists(path)) {
            Properties prop = new CamelCaseOrderedProperties();
            RuntimeUtil.loadProperties(prop, path);
            return TraitHelper.extractTraitsFromProperties(prop);
        } else {
            return null;
        }
    }

    protected String[] extractPropertiesTraits(File file) throws Exception {
        return extractPropertiesTraits(file.toPath());
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

    void setObserve(boolean observe) {
        this.observe = observe;
    }

    /**
     * Configurer used to customize internal options for the Export command.
     */
    public record ExportConfigurer(RuntimeType runtime,
            Path exportBaseDir,
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
            boolean packageScanJars,
            boolean quiet,
            boolean logging,
            String loggingLevel,
            boolean verbose,
            boolean skipPlugins) {
    }
}
