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

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Stack;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.vertx.VertxHttpClientFactory;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.file.FileSystemOptions;
import org.apache.camel.CamelContext;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.CommandHelper;
import org.apache.camel.dsl.jbang.core.commands.RunHelper;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.BaseTrait;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.MountTrait;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.TraitHelper;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Traits;
import org.apache.camel.dsl.jbang.core.common.Printer;
import org.apache.camel.dsl.jbang.core.common.RuntimeCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.dsl.jbang.core.common.RuntimeTypeConverter;
import org.apache.camel.dsl.jbang.core.common.SourceScheme;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.FileWatcherResourceReloadStrategy;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.concurrent.ThreadHelper;
import picocli.CommandLine;

import static org.apache.camel.dsl.jbang.core.commands.kubernetes.KubernetesHelper.getPodPhase;

@CommandLine.Command(name = "run", description = "Run Camel application on Kubernetes", sortOptions = false)
public class KubernetesRun extends KubernetesBaseCommand {

    @CommandLine.Parameters(description = "The Camel file(s) to run. If no files specified then application.properties is used as source for which files to run.",
                            arity = "0..9", paramLabel = "<files>", parameterConsumer = FilesConsumer.class)
    Path[] filePaths; // Defined only for file path completion; the field never used

    public List<String> files = new ArrayList<>();

    @CommandLine.Option(names = { "--service-account" }, description = "The service account used to run the application.")
    String serviceAccount;

    @CommandLine.Option(names = { "--prop", "--property" },
                        description = "Add a runtime property or properties from a file (syntax: [my-key=my-value|file:/path/to/my-conf.properties|/path/to/my-conf.properties].")
    String[] properties;

    @CommandLine.Option(names = { "--config" },
                        description = "Add a runtime configuration from a ConfigMap or a Secret (syntax: [configmap|secret]:name[/key], where name represents the configmap/secret name and key optionally represents the configmap/secret key to be filtered).")
    String[] configs;

    @CommandLine.Option(names = { "--resource" },
                        description = "Add a runtime resource from a Configmap or a Secret (syntax: [configmap|secret]:name[/key][@path], where name represents the configmap/secret name, key optionally represents the configmap/secret key to be filtered and path represents the destination path).")
    String[] resources;

    @CommandLine.Option(names = { "--open-api" }, description = "Add an OpenAPI spec (syntax: [configmap|file]:name).")
    String openApi;

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

    @CommandLine.Option(names = { "--wait" }, description = "Wait for the deployment to become ready.")
    boolean wait;

    @CommandLine.Option(names = { "--logs" }, description = "Print logs after Camel application has been started.")
    boolean logs;

    @CommandLine.Option(names = { "--reload", "--dev" },
                        description = "Enables dev mode (live reload when source files are updated and saved)")
    boolean dev;

    @CommandLine.Option(names = { "--quiet" }, description = "Quiet output - only show errors for build/deploy.")
    boolean quiet;

    @CommandLine.Option(names = { "--verbose" }, description = "Verbose output of build/deploy progress")
    boolean verbose;

    @CommandLine.Option(names = { "--cleanup" },
                        defaultValue = "true",
                        description = "Automatically removes deployment when process is stopped. Only in combination with --dev, --reload option.")
    boolean cleanup = true;

    @CommandLine.Option(names = { "--output" },
                        description = "Just output the generated integration custom resource (supports: yaml or json).")
    String output;

    // Export options

    @CommandLine.Option(names = { "--image" },
                        description = "The image name to be built.")
    String image;

    @CommandLine.Option(names = { "--image-registry" },
                        description = "The image registry to hold the app container image.")
    String imageRegistry;

    @CommandLine.Option(names = { "--image-group" },
                        description = "The image registry group used to push images to.")
    String imageGroup;

    @CommandLine.Option(names = { "--image-builder" }, defaultValue = "jib",
                        description = "The image builder used to build the container image (e.g. docker, jib, s2i).")
    String imageBuilder = "jib";

    @CommandLine.Option(names = { "--cluster-type" },
                        completionCandidates = ClusterTypeCompletionCandidates.class,
                        converter = ClusterTypeConverter.class,
                        description = "The target cluster type (${COMPLETION-CANDIDATES}). Special configurations may be applied to different cluster types such as Kind or Minikube.")
    String clusterType = "Kubernetes";

    @CommandLine.Option(names = { "--image-build" }, defaultValue = "true",
                        description = "Whether to build container image as part of the run.")
    boolean imageBuild = true;

    @CommandLine.Option(names = { "--image-push" }, defaultValue = "true",
                        description = "Whether to push image to given image registry as part of the run.")
    boolean imagePush = true;

    @CommandLine.Option(names = { "--image-platform" },
                        description = "List of target platforms. Each platform is defined using os and architecture (e.g. linux/amd64).")
    String[] imagePlatforms;

    @CommandLine.Option(names = { "--base-image" },
                        description = "The base image that is used to build the container image from (default is mirror.gcr.io/library/eclipse-temurin:<java-version>).")
    String baseImage;

    @CommandLine.Option(names = { "--registry-mirror" },
                        description = "Optional Docker registry mirror where to pull images from when building the container image.")
    String registryMirror;

    // Export base options

    @CommandLine.Option(names = { "--repo", "--repos" },
                        description = "Additional maven repositories (Use commas to separate multiple repositories)")
    String repositories;

    @CommandLine.Option(names = { "--dep", "--dependency" }, description = "Add additional dependencies",
                        split = ",")
    List<String> dependencies = new ArrayList<>();

    @CommandLine.Option(names = { "--runtime" },
                        completionCandidates = RuntimeCompletionCandidates.class,
                        defaultValue = "quarkus",
                        converter = RuntimeTypeConverter.class,
                        description = "Runtime (${COMPLETION-CANDIDATES})")
    RuntimeType runtime = RuntimeType.quarkus;

    @CommandLine.Option(names = { "--gav" }, description = "The Maven group:artifact:version")
    String gav;

    @CommandLine.Option(names = { "--exclude" }, description = "Exclude files by name or pattern")
    List<String> excludes = new ArrayList<>();

    @CommandLine.Option(names = { "--download" }, defaultValue = "true",
                        description = "Whether to allow automatic downloading JAR dependencies (over the internet)")
    boolean download = true;

    @CommandLine.Option(names = { "--package-scan-jars" }, defaultValue = "false",
                        description = "Whether to automatic package scan JARs for custom Spring or Quarkus beans making them available for Camel JBang")
    boolean packageScanJars;

    @CommandLine.Option(names = { "--maven-settings" },
                        description = "Optional location of Maven settings.xml file to configure servers, repositories, mirrors and proxies."
                                      + " If set to \"false\", not even the default ~/.m2/settings.xml will be used.")
    String mavenSettings;

    @CommandLine.Option(names = { "--maven-settings-security" },
                        description = "Optional location of Maven settings-security.xml file to decrypt settings.xml")
    String mavenSettingsSecurity;

    @CommandLine.Option(names = { "--maven-central-enabled" },
                        description = "Whether downloading JARs from Maven Central repository is enabled")
    boolean mavenCentralEnabled = true;

    @CommandLine.Option(names = { "--maven-apache-snapshot-enabled" },
                        description = "Whether downloading JARs from ASF Maven Snapshot repository is enabled")
    boolean mavenApacheSnapshotEnabled = true;

    @CommandLine.Option(names = { "--java-version" }, description = "Java version", defaultValue = "21")
    String javaVersion = "21";

    @CommandLine.Option(names = { "--camel-version" },
                        description = "To export using a different Camel version than the default version.")
    String camelVersion;

    @CommandLine.Option(names = {
            "--kamelets-version" }, description = "Apache Camel Kamelets version")
    String kameletsVersion;

    @CommandLine.Option(names = { "--profile" }, scope = CommandLine.ScopeType.INHERIT,
                        description = "Profile to export (dev, test, or prod).")
    String profile;

    @CommandLine.Option(names = { "--local-kamelet-dir" },
                        description = "Local directory for loading Kamelets (takes precedence)")
    String localKameletDir;

    @CommandLine.Option(names = { "--spring-boot-version" }, description = "Spring Boot version",
                        defaultValue = RuntimeType.SPRING_BOOT_VERSION)
    String springBootVersion = RuntimeType.SPRING_BOOT_VERSION;

    @CommandLine.Option(names = { "--camel-spring-boot-version" }, description = "Camel version to use with Spring Boot")
    String camelSpringBootVersion;

    @CommandLine.Option(names = { "--quarkus-group-id" }, description = "Quarkus Platform Maven groupId",
                        defaultValue = "io.quarkus.platform")
    String quarkusGroupId = "io.quarkus.platform";

    @CommandLine.Option(names = { "--quarkus-artifact-id" }, description = "Quarkus Platform Maven artifactId",
                        defaultValue = "quarkus-bom")
    String quarkusArtifactId = "quarkus-bom";

    @CommandLine.Option(names = { "--quarkus-version" }, description = "Quarkus Platform version",
                        defaultValue = RuntimeType.QUARKUS_VERSION)
    String quarkusVersion = RuntimeType.QUARKUS_VERSION;

    @CommandLine.Option(names = { "--package-name" },
                        description = "For Java source files should they have the given package name. By default the package name is computed from the Maven GAV. "
                                      + "Use false to turn off and not include package name in the Java source files.")
    String packageName;

    @CommandLine.Option(names = { "--build-property" },
                        description = "Maven/Gradle build properties, ex. --build-property=prop1=foo")
    List<String> buildProperties = new ArrayList<>();

    @CommandLine.Option(names = { "--disable-auto" },
                        description = "Disable automatic cluster type detection and automatic settings for cluster.")
    boolean disableAuto = false;

    @CommandLine.Option(names = { "--skip-plugins" }, defaultValue = "false",
                        description = "Skip plugins during export")
    boolean skipPlugins = false;

    // DevMode/Reload state
    private CamelContext devModeContext;
    private Thread devModeShutdownTask;
    private int devModeReloadCount;

    private KubernetesPodLogs reusablePodLogs;
    private Printer quietPrinter;
    private Traits computedTraits;

    public KubernetesRun(CamelJBangMain main) {
        this(main, null);
    }

    public KubernetesRun(CamelJBangMain main, List<String> files) {
        super(main);
        if (files != null) {
            this.files.addAll(files);
        }
        projectNameSuppliers.add(() -> projectNameFromImage(() -> image));
        projectNameSuppliers.add(() -> projectNameFromGav(() -> gav));
        projectNameSuppliers.add(() -> projectNameFromFilePath(this::firstFilePath));
    }

    private String firstFilePath() {
        return !files.isEmpty() ? files.get(0) : null;
    }

    public Integer doCall() throws Exception {
        String projectName = getProjectName();
        computedTraits = TraitHelper.parseTraits(traits);

        Path baseDir = Path.of(".");
        if (files.size() == 1) {
            String name = FileUtil.stripTrailingSeparator(files.get(0));
            Path first = Path.of(name);
            if (Files.isDirectory(first)) {
                baseDir = first;
                RunHelper.dirToFiles(name, files);
            }
        }
        // merge the properties from files
        if (properties != null) {
            List<String> definiteProperties = new ArrayList<>();
            for (String p : properties) {
                if (p.startsWith("file:") || p.contains(File.separator)) {
                    String filename = p.startsWith("file:") ? p.substring("file:".length()) : p;
                    File f = new File(filename);
                    if (f.exists()) {
                        Properties prop = new Properties();
                        try (FileInputStream input = new FileInputStream(f)) {
                            prop.load(input);
                        }
                        prop.forEach((k, v) -> definiteProperties.add(k + "=" + v));
                    }
                } else {
                    definiteProperties.add(p);
                }
            }
            properties = definiteProperties.toArray(new String[definiteProperties.size()]);
        }
        // when user sets configuration or resources from configmap/secret
        // the projected properties files must be set in the app runtime
        setPropertiesLocation();

        String workingDir = getIndexedWorkingDir(projectName);
        KubernetesExport export = configureExport(workingDir, baseDir);
        boolean cronEnabled = computedTraits.getCronjob() != null && computedTraits.getCronjob().getEnabled();
        if (cronEnabled) {
            // disable observability-services as CronJob doesn't use the container probes
            export.setObserve(false);
        }
        int exit = export.export();
        if (exit != 0) {
            printer().printErr("Project export failed!");
            return exit;
        }

        if (output != null) {
            boolean ksvcEnabled = computedTraits.getKnativeService() != null && computedTraits.getKnativeService().getEnabled();

            exit = buildProjectOutput(workingDir);
            if (exit != 0) {
                printer().printErr("Project build failed!");
                return exit;
            }

            Path manifestPath;
            switch (output) {
                case "yaml" -> {
                    if (ksvcEnabled) {
                        // trick the clusterType to be able to read from the jkube source directory
                        manifestPath = KubernetesHelper.resolveKubernetesManifestPath("service",
                                Paths.get(workingDir, "src/main/jkube"));
                    } else {
                        manifestPath = KubernetesHelper.resolveKubernetesManifestPath(clusterType,
                                Paths.get(workingDir, "target/kubernetes"));
                    }
                }
                case "json" ->
                    manifestPath = KubernetesHelper.resolveKubernetesManifestPath(clusterType,
                            Paths.get(workingDir, "target/kubernetes"),
                            "json");
                default -> {
                    printer().printErr("Unsupported output format '%s' (supported: yaml, json)".formatted(output));
                    return 1;
                }
            }

            try (InputStream is = Files.newInputStream(manifestPath)) {
                super.printer().println(IOHelper.loadText(is));
            }

            return 0;
        }

        exit = deployProject(workingDir, false);
        if (exit != 0) {
            printer().printErr("Project deploy failed!");
            return exit;
        }

        if (dev || wait || logs) {
            waitForRunningPod(projectName);
        }

        if (dev) {
            setupDevMode(projectName, workingDir, baseDir);
        }

        if (dev || logs) {
            startPodLogging(projectName);
        }

        return 0;
    }

    private String getIndexedWorkingDir(String projectName) {
        var workingDir = RUN_PLATFORM_DIR + "/" + projectName;
        if (devModeReloadCount > 0) {
            workingDir += "-%03d".formatted(devModeReloadCount);
        }
        return workingDir;
    }

    private KubernetesExport configureExport(String workingDir, Path baseDir) {
        detectCluster();
        KubernetesExport.ExportConfigurer configurer = new KubernetesExport.ExportConfigurer(
                runtime,
                baseDir,
                quarkusVersion,
                files,
                name,
                gav,
                repositories,
                dependencies,
                excludes,
                mavenSettings,
                mavenSettingsSecurity,
                mavenCentralEnabled,
                mavenApacheSnapshotEnabled,
                javaVersion,
                camelVersion,
                kameletsVersion,
                profile,
                localKameletDir,
                springBootVersion,
                camelSpringBootVersion,
                quarkusGroupId,
                quarkusArtifactId,
                "maven",
                openApi,
                workingDir,
                packageName,
                buildProperties,
                true,
                false,
                true,
                true,
                false,
                true,
                download,
                packageScanJars,
                (quiet || output != null),
                true,
                "info",
                verbose,
                skipPlugins);
        KubernetesExport export = new KubernetesExport(getMain(), configurer);

        export.image = image;
        export.imageRegistry = imageRegistry;
        export.imageGroup = imageGroup;
        export.imagePush = imagePush;
        export.imageBuilder = imageBuilder;
        export.imagePlatforms = imagePlatforms;
        export.baseImage = baseImage;
        export.registryMirror = registryMirror;
        export.clusterType = clusterType;
        export.serviceAccount = serviceAccount;
        export.setApplicationProperties(properties);
        export.configs = configs;
        export.resources = resources;
        export.envVars = envVars;
        export.volumes = volumes;
        export.connects = connects;
        export.annotations = annotations;
        export.labels = labels;
        export.traits = traits;

        return export;
    }

    private void setupDevMode(String projectName, String workingDir, Path baseDir) throws Exception {
        String firstPath = firstFilePath();

        String watchDir = ".";
        FileFilter filter = null;
        if (firstPath != null) {
            String filePath = FileUtil.onlyPath(SourceScheme.onlyName(firstPath));
            if (filePath != null) {
                watchDir = filePath;
            }

            filter = pathname -> files.stream()
                    .map(FileUtil::stripPath)
                    .anyMatch(name -> name.equals(pathname.getName()));
        }

        FileWatcherResourceReloadStrategy reloadStrategy = new FileWatcherResourceReloadStrategy(watchDir);
        reloadStrategy.setResourceReload((name, resource) -> {
            synchronized (this) {

                printer().printf("Reloading project due to file change: %s%n", FileUtil.stripPath(name));

                devModeReloadCount += 1;

                String reloadWorkingDir = getIndexedWorkingDir(projectName);
                devModeContext.close();

                // Re-export updated project
                //
                KubernetesExport export = configureExport(reloadWorkingDir, baseDir);
                int exit = export.export();
                if (exit != 0) {
                    printer().printErr("Project (re)export failed for: %s".formatted(reloadWorkingDir));
                    return;
                }

                reusablePodLogs.retryForReload = true;
                try {

                    // Undeploy/Delete current project
                    //
                    KubernetesDelete deleteCommand = new KubernetesDelete(getMain());
                    deleteCommand.name = projectName;
                    deleteCommand.doCall();

                    // Re-deploy updated project
                    //
                    exit = deployProject(reloadWorkingDir, true);
                    if (exit != 0) {
                        printer().printErr("Project redeploy failed for: %s".formatted(reloadWorkingDir));
                        return;
                    }

                    waitForRunningPod(projectName);

                } finally {
                    reusablePodLogs.retryForReload = false;
                }

                // Recursively setup --dev mode for updated project
                //
                Runtime.getRuntime().removeShutdownHook(devModeShutdownTask);
                setupDevMode(projectName, reloadWorkingDir, baseDir);

                printer().printf("Project reloaded: %s%n", reloadWorkingDir);
            }
        });
        if (filter != null) {
            reloadStrategy.setFileFilter(filter);
        }

        devModeContext = new DefaultCamelContext(false);
        devModeContext.addService(reloadStrategy);
        devModeContext.start();

        if (cleanup) {
            installShutdownHook(projectName, workingDir);
        }
    }

    private void startPodLogging(String projectName) throws Exception {
        try {
            reusablePodLogs = new KubernetesPodLogs(getMain());
            if (!ObjectHelper.isEmpty(namespace)) {
                reusablePodLogs.namespace = namespace;
            }
            reusablePodLogs.name = projectName;
            reusablePodLogs.doCall();
        } catch (Exception e) {
            printer().printErr("Failed to read pod logs", e);
            throw e;
        }
    }

    private void waitForRunningPod(String projectName) {
        if (!quiet) {
            String kubectlCmd = "kubectl get pod";
            kubectlCmd += " -l %s=%s".formatted(BaseTrait.KUBERNETES_LABEL_NAME, projectName);
            if (!ObjectHelper.isEmpty(namespace)) {
                kubectlCmd += " -n %s".formatted(namespace);
            }
            printer().println("Run: " + kubectlCmd);
        }
        var pod = client(Pod.class).withLabel(BaseTrait.KUBERNETES_LABEL_NAME, projectName)
                .waitUntilCondition(it -> "Running".equals(getPodPhase(it)), 10, TimeUnit.MINUTES);
        printer().println(String.format("Pod '%s' in phase %s", pod.getMetadata().getName(), getPodPhase(pod)));
    }

    private void installShutdownHook(String projectName, String workingDir) {
        devModeShutdownTask = new Thread(() -> {
            KubernetesDelete deleteCommand = new KubernetesDelete(getMain());
            deleteCommand.name = projectName;
            try (var client = createKubernetesClientForShutdownHook()) {
                KubernetesHelper.setKubernetesClient(client);
                deleteCommand.doCall();
                CommandHelper.cleanExportDir(workingDir, false);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        devModeShutdownTask.setName(ThreadHelper.resolveThreadName(null, "CamelShutdownInterceptor"));
        Runtime.getRuntime().addShutdownHook(devModeShutdownTask);
    }

    // KubernetesClientVertx can no longer be used in ShutdownHook
    // https://issues.apache.org/jira/browse/CAMEL-21621
    private KubernetesClient createKubernetesClientForShutdownHook() {
        System.setProperty("vertx.disableDnsResolver", "true");
        var vertx = Vertx.vertx((new VertxOptions())
                .setFileSystemOptions((new FileSystemOptions())
                        .setFileCachingEnabled(false)
                        .setClassPathResolvingEnabled(false))
                .setUseDaemonThread(true));
        var client = new KubernetesClientBuilder()
                .withHttpClientFactory(new VertxHttpClientFactory(vertx))
                .build();
        return client;
    }

    // build the project locally to generate the artifacts in the target directory, but don't deploy it.
    private Integer buildProjectOutput(String workingDir) throws IOException, InterruptedException {
        printer().println("Building Camel application ...");

        // Run build via Maven
        String mvnw = "/mvnw";
        if (FileUtil.isWindows()) {
            mvnw = "/mvnw.cmd";
        }
        ProcessBuilder pb = new ProcessBuilder();
        List<String> args = new ArrayList<>();

        args.add(workingDir + mvnw);
        if (quiet || !verbose) {
            args.add("--quiet");
        }
        args.add("--file");
        args.add(workingDir);

        if (!ObjectHelper.isEmpty(namespace)) {
            args.add("-Djkube.namespace=%s".formatted(namespace));
        }

        // skip image build and push because we only want to build the Kubernetes manifest
        args.add("-Djkube.skip.build=true");
        args.add("-Djkube.skip.push=true");
        // suppress maven transfer progress
        args.add("-ntp");

        if (computedTraits.getKnativeService() != null && computedTraits.getKnativeService().getEnabled()) {
            // by default jkube creates a Deployment manifest and it doesn't support knative controller yet.
            // however when knative-service is enabled the knative-service trait generates a src/main/jkube/service.yml
            // and there is no need for the regular Deployment as the knative Service manifest, once deployed
            // will generate the regular Deployment, so we have to disable the jkube resources task to not run and not generate the deployment.yml
            args.add("-Djkube.skip.resource=true");
        }

        args.add("package");

        printer().println("Run: " + String.join(" ", args));

        pb.command(args.toArray(String[]::new));

        if (quiet || !verbose) {
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        } else {
            pb.inheritIO(); // run in foreground (with IO so logs are visible)
        }

        Process p = pb.start();
        // wait for that process to exit as we run in foreground
        int exit = p.waitFor();
        if (exit != 0) {
            printer().printErr("Build failed!");
            return exit;
        }

        return 0;
    }

    private Integer deployProject(String workingDir, boolean reload) throws Exception {

        printer().println("Deploying to %s ...".formatted(clusterType));

        // Run build via Maven
        String mvnw = "/mvnw";
        if (FileUtil.isWindows()) {
            mvnw = "/mvnw.cmd";
        }
        ProcessBuilder pb = new ProcessBuilder();
        List<String> args = new ArrayList<>();

        args.add(workingDir + mvnw);
        if (quiet || !verbose) {
            args.add("--quiet");
        }
        // suppress maven transfer progress
        args.add("-ntp");
        args.add("--file");
        args.add(workingDir);

        if (!imageBuild) {
            args.add("-Djkube.skip.build=true");
        }

        if (imagePush) {
            args.add("-Djkube.%s.push=true".formatted(imageBuilder));
            args.add("-Djkube.skip.push=false");
        }

        if (!ObjectHelper.isEmpty(namespace)) {
            args.add("-Djkube.namespace=%s".formatted(namespace));
        }

        boolean isOpenshift = ClusterType.OPENSHIFT.isEqualTo(clusterType);
        var prefix = isOpenshift ? "oc" : "k8s";
        if (computedTraits.getKnativeService() != null && computedTraits.getKnativeService().getEnabled()) {
            // by default jkube creates a Deployment manifest and it doesn't support knative controller yet.
            // however when knative-service is enabled the knative-service trait generates a src/main/jkube/service.yml
            // and there is no need for the regular Deployment as the knative Service manifest, once deployed
            // will generate the regular Deployment, so we have to disable the jkube resources task to not run and not generate the deployment.yml
            // apply the knative service manifest and specify the knative service.yml
            args.add("-Djkube.skip.resource=true");
            args.add(prefix + ":build");
            args.add(prefix + ":apply");
            if (isOpenshift) {
                args.add("-Djkube.openshiftManifest=src/main/jkube/service.yml");
            } else {
                args.add("-Djkube.kubernetesManifest=src/main/jkube/service.yml");
            }
            if (ClusterType.MINIKUBE.isEqualTo(clusterType) && !disableAuto) {
                KubernetesHelper.skipKnativeImageTagResolutionInMinikube();
            }
        } else {
            args.add(prefix + ":deploy");
        }

        printer().println("Run: " + String.join(" ", args));

        pb.command(args.toArray(String[]::new));

        if (quiet || !verbose) {
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        } else {
            pb.inheritIO(); // run in foreground (with IO so logs are visible)
        }

        Process p = pb.start();
        // wait for that process to exit as we run in foreground
        int exit = p.waitFor();
        if (exit != 0) {
            String msg = "Deployment to %s failed!";
            if (!verbose) {
                msg += " (use --verbose for more details)";
            }
            printer().printErr(msg.formatted(clusterType));
            return exit;
        }

        return 0;
    }

    private void detectCluster() {
        if (!disableAuto) {
            if (verbose) {
                printer().print("Automatic Kubernetes cluster detection... ");
            }
            ClusterType cluster = KubernetesHelper.discoverClusterType();
            this.clusterType = cluster.name();
            if (ClusterType.MINIKUBE == cluster) {
                this.imageBuilder = "docker";
                this.imagePush = false;
            }
            if (verbose) {
                printer().println(this.clusterType);
            }
        }
    }

    /*
     * When a configmap/secret is projected as a properties file and mounted into the running pod
     * and the properties file must be set at runtime to be discovered by the camel runtime
     * with the camel.component.properties.location property.
     * This is only for the --config parameter as the --resource are for any file type, not configurations.
     */
    private void setPropertiesLocation() {
        if (configs != null) {
            StringJoiner propertiesLocation = new StringJoiner(",");
            for (String c : configs) {
                if (c.startsWith("configmap:")) {
                    String name = c.substring("configmap:".length());
                    // in case the user has set a property to filter from the configmap, the "name" var is
                    // the configmap name and the projected file.
                    if (name.contains("/")) {
                        propertiesLocation.add("file:" + MountTrait.CONF_DIR + MountTrait.CONFIGMAPS + "/" + name);
                    } else {
                        // we have to inspect the configmap and retrieve the key names, as they are
                        // mapped to the mounted file names.
                        Set<String> configmapKeys = retrieveConfigmapKeys(name);
                        configmapKeys.forEach(key -> propertiesLocation
                                .add("file:" + MountTrait.CONF_DIR + MountTrait.CONFIGMAPS + "/" + name + "/" + key));
                    }
                } else if (c.startsWith("secret:")) {
                    String name = c.substring("secret:".length());
                    if (name.contains("/")) {
                        propertiesLocation.add("file:" + MountTrait.CONF_DIR + MountTrait.SECRETS + "/" + name);
                    } else {
                        Set<String> secretKeys = retrieveSecretKeys(name);
                        secretKeys.forEach(key -> propertiesLocation
                                .add("file:" + MountTrait.CONF_DIR + MountTrait.SECRETS + "/" + name + "/" + key));
                    }
                }
            }
            if (propertiesLocation.length() > 0) {
                List<String> definiteProperties = new ArrayList<>();
                definiteProperties.add("camel.component.properties.ignore-missing-location=true");
                definiteProperties.add("camel.component.properties.location=" + propertiesLocation);
                if (properties != null && properties.length > 0) {
                    for (String s : properties) {
                        definiteProperties.add(s);
                    }
                }
                properties = definiteProperties.toArray(new String[definiteProperties.size()]);
            }
        }
    }

    private Set<String> retrieveConfigmapKeys(String name) {
        KubernetesClient client = client();
        String ns = "default";
        if (namespace != null || client.getNamespace() != null) {
            ns = namespace != null ? namespace : client.getNamespace();
        }
        Map<String, String> data = client.configMaps().inNamespace(ns).withName(name).get().getData();
        return data.keySet();
    }

    private Set<String> retrieveSecretKeys(String name) {
        KubernetesClient client = client();
        String ns = "default";
        if (namespace != null || client.getNamespace() != null) {
            ns = namespace != null ? namespace : client.getNamespace();
        }
        Map<String, String> data = client.secrets().inNamespace(ns).withName(name).get().getData();
        return data.keySet();
    }

    @Override
    protected Printer printer() {
        if (quiet || output != null) {
            if (quietPrinter == null) {
                quietPrinter = new Printer.QuietPrinter(super.printer());
            }

            CommandHelper.setPrinter(quietPrinter);
            return quietPrinter;
        }
        return super.printer();
    }

    static class FilesConsumer extends ParameterConsumer<KubernetesRun> {
        @Override
        protected void doConsumeParameters(Stack<String> args, KubernetesRun cmd) {
            String arg = args.pop();
            cmd.files.add(arg);
        }
    }

}
