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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
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
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.BaseTrait;
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

    @CommandLine.Parameters(description = "The Camel file(s) to run.",
                            arity = "0..9", paramLabel = "<files>")
    String[] filePaths;

    @CommandLine.Option(names = { "--service-account" }, description = "The service account used to run the application.")
    String serviceAccount;

    @CommandLine.Option(names = { "--property" },
                        description = "Add a runtime property or properties file from a path, a config map or a secret (syntax: [my-key=my-value|file:/path/to/my-conf.properties|[configmap|secret]:name]).")
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
                        description = "The image builder used to build the container image (e.g. docker, jib, podman).")
    String imageBuilder;

    @CommandLine.Option(names = { "--cluster-type" },
                        description = "The target cluster type. Special configurations may be applied to different cluster types such as Kind or Minikube.")
    String clusterType = "Kubernetes";

    @CommandLine.Option(names = { "--image-build" }, defaultValue = "true",
                        description = "Whether to build container image as part of the run.")
    boolean imageBuild = true;

    @CommandLine.Option(names = { "--image-push" }, defaultValue = "false",
                        description = "Whether to push image to given image registry as part of the run.")
    boolean imagePush = false;

    @CommandLine.Option(names = { "--image-platforms" },
                        description = "List of target platforms. Each platform is defined using the pattern.")
    String imagePlatforms;

    // Export base options

    @CommandLine.Option(names = { "--repos" },
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

    @CommandLine.Option(names = { "--java-version" }, description = "Java version", defaultValue = "17")
    String javaVersion = "17";

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
                        defaultValue = "${sys:camel.jbang.springBootVersion:-" + RuntimeType.SPRING_BOOT_VERSION + "}")
    String springBootVersion = Objects.requireNonNullElse(System.getProperty("camel.jbang.springBootVersion"), RuntimeType.SPRING_BOOT_VERSION);

    @CommandLine.Option(names = { "--camel-spring-boot-version" }, description = "Camel version to use with Spring Boot")
    String camelSpringBootVersion;

    @CommandLine.Option(names = { "--quarkus-group-id" }, description = "Quarkus Platform Maven groupId",
                        defaultValue = "io.quarkus.platform")
    String quarkusGroupId = "io.quarkus.platform";

    @CommandLine.Option(names = { "--quarkus-artifact-id" }, description = "Quarkus Platform Maven artifactId",
                        defaultValue = "quarkus-bom")
    String quarkusArtifactId = "quarkus-bom";

    @CommandLine.Option(names = { "--quarkus-version" }, description = "Quarkus Platform version",
                        defaultValue = "${sys:camel.jbang.quarkusVersion:-" + RuntimeType.QUARKUS_VERSION + "}")
    String quarkusVersion = Objects.requireNonNullElse(System.getProperty("camel.jbang.quarkusVersion"), RuntimeType.QUARKUS_VERSION);

    @CommandLine.Option(names = { "--package-name" },
                        description = "For Java source files should they have the given package name. By default the package name is computed from the Maven GAV. "
                                      + "Use false to turn off and not include package name in the Java source files.")
    String packageName;

    @CommandLine.Option(names = { "--build-property" },
                        description = "Maven/Gradle build properties, ex. --build-property=prop1=foo")
    List<String> buildProperties = new ArrayList<>();

    // DevMode/Reload state
    private CamelContext devModeContext;
    private Thread devModeShutdownTask;
    private int devModeReloadCount;

    private KubernetesPodLogs reusablePodLogs;

    public KubernetesRun(CamelJBangMain main) {
        this(main, null);
    }

    public KubernetesRun(CamelJBangMain main, String[] files) {
        super(main);
        filePaths = files;
        projectNameSuppliers.add(() -> projectNameFromImage(() -> image));
        projectNameSuppliers.add(() -> projectNameFromGav(() -> gav));
        projectNameSuppliers.add(() -> projectNameFromFilePath(() -> firstFilePath()));
    }

    private String firstFilePath() {
        return filePaths != null && filePaths.length > 0 ? filePaths[0] : null;
    }

    public Integer doCall() throws Exception {
        String projectName = getProjectName();

        String workingDir = getIndexedWorkingDir(projectName);
        KubernetesExport export = configureExport(workingDir);
        int exit = export.export();
        if (exit != 0) {
            printer().println("Project export failed!");
            return exit;
        }

        if (output != null) {

            exit = buildProject(workingDir);
            if (exit != 0) {
                printer().println("Project build failed!");
                return exit;
            }

            File manifest;
            switch (output) {
                case "yaml" ->
                    manifest = KubernetesHelper.resolveKubernetesManifest(clusterType, workingDir + "/target/kubernetes");
                case "json" ->
                    manifest = KubernetesHelper.resolveKubernetesManifest(clusterType, workingDir + "/target/kubernetes",
                            "json");
                default -> {
                    printer().printf("Unsupported output format '%s' (supported: yaml, json)%n", output);
                    return 1;
                }
            }

            try (FileInputStream fis = new FileInputStream(manifest)) {
                printer().println(IOHelper.loadText(fis));
            }

            return 0;
        }

        exit = deployProject(workingDir, false);
        if (exit != 0) {
            printer().println("Project deploy failed!");
            return exit;
        }

        if (dev || wait || logs) {
            waitForRunningPod(projectName);
        }

        if (dev) {
            setupDevMode(projectName, workingDir);
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

    private KubernetesExport configureExport(String workingDir) {
        KubernetesExport.ExportConfigurer configurer = new KubernetesExport.ExportConfigurer(
                runtime,
                quarkusVersion,
                List.of(filePaths),
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
                false,
                true,
                false,
                true,
                true,
                false,
                false,
                "off");
        KubernetesExport export = new KubernetesExport(getMain(), configurer);

        export.image = image;
        export.imageRegistry = imageRegistry;
        export.imageGroup = imageGroup;
        export.imageBuilder = imageBuilder;
        export.clusterType = clusterType;
        export.serviceAccount = serviceAccount;
        export.properties = properties;
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

    private void setupDevMode(String projectName, String workingDir) throws Exception {
        String firstPath = firstFilePath();

        String watchDir = ".";
        FileFilter filter = null;
        if (firstPath != null) {
            String filePath = FileUtil.onlyPath(SourceScheme.onlyName(firstPath));
            if (filePath != null) {
                watchDir = filePath;
            }

            filter = pathname -> Arrays.stream(filePaths)
                    .map(FileUtil::stripPath)
                    .anyMatch(name -> name.equals(pathname.getName()));
        }

        FileWatcherResourceReloadStrategy reloadStrategy = new FileWatcherResourceReloadStrategy(watchDir);
        reloadStrategy.setResourceReload((name, resource) -> {
            synchronized (this) {

                printer().printf("Reloading project due to file change: %s%n", FileUtil.stripPath(name));

                String currentWorkingDir = getIndexedWorkingDir(projectName);
                devModeReloadCount += 1;

                String reloadWorkingDir = getIndexedWorkingDir(projectName);
                devModeContext.close();

                // Re-export updated project
                //
                KubernetesExport export = configureExport(reloadWorkingDir);
                int exit = export.export();
                if (exit != 0) {
                    printer().printf("Project reexport failed for: %s%n", reloadWorkingDir);
                    return;
                }

                reusablePodLogs.retryForReload = true;
                try {

                    // Undeploy/Delete current project
                    //
                    KubernetesDelete deleteCommand = new KubernetesDelete(getMain());
                    deleteCommand.workingDir = currentWorkingDir;
                    deleteCommand.clusterType = clusterType;
                    deleteCommand.name = projectName;
                    deleteCommand.doCall();

                    // Re-deploy updated project
                    //
                    exit = deployProject(reloadWorkingDir, true);
                    if (exit != 0) {
                        printer().printf("Project redeploy failed for: %s%n", reloadWorkingDir);
                        return;
                    }

                    waitForRunningPod(projectName);

                } finally {
                    reusablePodLogs.retryForReload = false;
                }

                // Recursively setup --dev mode for updated project
                //
                Runtime.getRuntime().removeShutdownHook(devModeShutdownTask);
                setupDevMode(projectName, reloadWorkingDir);

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
            printer().println("Failed to read pod logs - " + e);
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
        if (!quiet) {
            printer().println(String.format("Pod '%s' in phase %s", pod.getMetadata().getName(), getPodPhase(pod)));
        }
    }

    private void installShutdownHook(String projectName, String workingDir) {
        devModeShutdownTask = new Thread(() -> {
            KubernetesDelete deleteCommand = new KubernetesDelete(getMain());
            deleteCommand.clusterType = clusterType;
            deleteCommand.workingDir = workingDir;
            deleteCommand.name = projectName;
            try (var client = createKubernetesClientForShutdownHook()) {
                KubernetesHelper.setKubernetesClient(client);
                deleteCommand.doCall();
                CommandHelper.cleanExportDir(deleteCommand.workingDir, false);
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

    private Integer buildProject(String workingDir) throws IOException, InterruptedException {
        printer().println("Building Camel application ...");

        // Run build via Maven
        String mvnw = "/mvnw";
        if (FileUtil.isWindows()) {
            mvnw = "/mvnw.cmd";
        }
        ProcessBuilder pb = new ProcessBuilder();
        List<String> args = new ArrayList<>();

        args.add(workingDir + mvnw);
        if (quiet) {
            args.add("--quiet");
        }
        args.add("--file");
        args.add(workingDir);

        if (!ObjectHelper.isEmpty(namespace)) {
            args.add("-Djkube.namespace=%s".formatted(namespace));
        }

        args.add("package");

        if (!quiet) {
            printer().println("Run: " + String.join(" ", args));
        }

        pb.command(args.toArray(String[]::new));

        pb.inheritIO(); // run in foreground (with IO so logs are visible)
        Process p = pb.start();
        // wait for that process to exit as we run in foreground
        int exit = p.waitFor();
        if (exit != 0) {
            printer().println("Build failed!");
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
        if (quiet) {
            args.add("--quiet");
        }
        args.add("--file");
        args.add(workingDir);

        if (!imageBuild) {
            args.add("-Djkube.skip.build=true");
        }

        if (imagePush) {
            args.add("-Djkube.%s.push=true".formatted(imageBuilder));
        }

        if (!ObjectHelper.isEmpty(namespace)) {
            args.add("-Djkube.namespace=%s".formatted(namespace));
        }

        boolean isOpenshift = ClusterType.OPENSHIFT.isEqualTo(clusterType);
        var prefix = isOpenshift ? "oc" : "k8s";
        args.add(prefix + ":deploy");

        if (!quiet) {
            printer().println("Run: " + String.join(" ", args));
        }

        pb.command(args.toArray(String[]::new));

        pb.inheritIO(); // run in foreground (with IO so logs are visible)
        Process p = pb.start();
        // wait for that process to exit as we run in foreground
        int exit = p.waitFor();
        if (exit != 0) {
            printer().println("Deployment to %s failed!".formatted(clusterType));
            return exit;
        }

        return 0;
    }
}
