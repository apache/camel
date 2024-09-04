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
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.fabric8.kubernetes.api.model.Pod;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.BaseTrait;
import org.apache.camel.dsl.jbang.core.common.Printer;
import org.apache.camel.dsl.jbang.core.common.RuntimeCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.dsl.jbang.core.common.RuntimeTypeConverter;
import org.apache.camel.dsl.jbang.core.common.SourceScheme;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.FileWatcherResourceReloadStrategy;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.concurrent.ThreadHelper;
import picocli.CommandLine;

@CommandLine.Command(name = "run", description = "Run Camel application on Kubernetes", sortOptions = false)
public class KubernetesRun extends KubernetesBaseCommand {

    @CommandLine.Parameters(description = "The Camel file(s) to run.",
                            arity = "0..9", paramLabel = "<files>")
    String[] filePaths;

    @CommandLine.Option(names = { "--trait-profile" }, description = "The trait profile to use for the deployment.")
    String traitProfile;

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

    @CommandLine.Option(names = { "--image-builder" },
                        description = "The image builder used to build the container image (e.g. docker, jib, podman, s2i).")
    String imageBuilder;

    @CommandLine.Option(names = { "--cluster-type" },
                        description = "The target cluster type. Special configurations may be applied to different cluster types such as Kind or Minikube.")
    String clusterType;

    @CommandLine.Option(names = { "--image-build" },
                        defaultValue = "true",
                        description = "Weather to build container image as part of the run.")
    boolean imageBuild = true;

    @CommandLine.Option(names = { "--image-push" },
                        defaultValue = "true",
                        description = "Weather to push image to given image registry as part of the run.")
    boolean imagePush = true;

    @CommandLine.Option(names = { "--image-platforms" },
                        description = "List of target platforms. Each platform is defined using the pattern.")
    String imagePlatforms;

    // Export base options

    @CommandLine.Option(names = { "--repository" }, description = "Additional maven repositories")
    List<String> repositories = new ArrayList<>();

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

    public KubernetesRun(CamelJBangMain main) {
        super(main);
    }

    public Integer doCall() throws Exception {
        String projectName = getProjectName();

        String workingDir = RUN_PLATFORM_DIR + "/" + projectName;

        printer().println("Exporting application ...");

        // Cache export output in String for later usage in case of error
        Printer runPrinter = printer();
        StringPrinter exportPrinter = new StringPrinter();
        getMain().withPrinter(exportPrinter);

        KubernetesExport export = configureExport(workingDir);
        int exit = export.export();

        // Revert printer to this run command's printer
        getMain().withPrinter(runPrinter);
        if (exit != 0) {
            // print export command output with error details
            printer().println(exportPrinter.getOutput());
            return exit;
        }

        if (output != null) {
            if (RuntimeType.quarkus == runtime) {
                exit = buildQuarkus(workingDir);
            } else if (RuntimeType.springBoot == runtime) {
                exit = buildSpringBoot(workingDir);
            }

            if (exit != 0) {
                printer().println("Project build failed!");
                return exit;
            }

            File manifest;
            switch (output) {
                case "yaml" -> manifest = KubernetesHelper.resolveKubernetesManifest(workingDir + "/target/kubernetes");
                case "json" ->
                    manifest = KubernetesHelper.resolveKubernetesManifest(workingDir + "/target/kubernetes", "json");
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

        if (RuntimeType.quarkus == runtime) {
            exit = deployQuarkus(workingDir);
        } else if (RuntimeType.springBoot == runtime) {
            exit = deploySpringBoot(workingDir);
        }

        if (exit != 0) {
            printer().println("Deployment to %s failed!".formatted(Optional.ofNullable(clusterType)
                    .map(StringHelper::capitalize).orElse("Kubernetes")));
            return exit;
        }

        if (dev) {
            DefaultCamelContext reloadContext = new DefaultCamelContext(false);
            configureFileWatch(reloadContext, export, workingDir);
            reloadContext.start();

            if (cleanup) {
                installShutdownInterceptor(projectName, workingDir);
            }
        }

        if (dev || wait || logs) {
            client(Pod.class).withLabel(BaseTrait.INTEGRATION_LABEL, projectName)
                    .waitUntilCondition(it -> "Running".equals(it.getStatus().getPhase()), 10, TimeUnit.MINUTES);
        }

        if (dev || logs) {
            PodLogs logsCommand = new PodLogs(getMain());
            logsCommand.withClient(client());
            logsCommand.label = "%s=%s".formatted(BaseTrait.INTEGRATION_LABEL, projectName);
            logsCommand.doCall();
        }

        return 0;
    }

    private KubernetesExport configureExport(String workingDir) {
        KubernetesExport.ExportConfigurer configurer = new KubernetesExport.ExportConfigurer(
                runtime,
                quarkusVersion,
                List.of(filePaths),
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
        export.traitProfile = traitProfile;
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

    private void installShutdownInterceptor(String projectName, String workingDir) {
        KubernetesDelete deleteCommand = new KubernetesDelete(getMain());
        deleteCommand.name = projectName;
        deleteCommand.workingDir = workingDir;

        Thread task = new Thread(() -> {
            try {
                deleteCommand.doCall();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        task.setName(ThreadHelper.resolveThreadName(null, "CamelShutdownInterceptor"));
        Runtime.getRuntime().addShutdownHook(task);
    }

    private Integer buildQuarkus(String workingDir) throws IOException, InterruptedException {
        printer().println("Building Quarkus application ...");

        // Run Quarkus build via Maven
        String mvnw = "/mvnw";
        if (FileUtil.isWindows()) {
            mvnw = "/mvnw.cmd";
        }
        ProcessBuilder pb = new ProcessBuilder();
        List<String> args = new ArrayList<>();

        args.add(workingDir + mvnw);
        args.add("--quiet");
        args.add("--file");
        args.add(workingDir);
        args.add("package");
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

    private Integer deployQuarkus(String workingDir) throws IOException, InterruptedException {
        printer().println("Deploying to %s ...".formatted(Optional.ofNullable(clusterType)
                .map(StringHelper::capitalize).orElse("Kubernetes")));

        // Run Quarkus build via Maven
        String mvnw = "/mvnw";
        if (FileUtil.isWindows()) {
            mvnw = "/mvnw.cmd";
        }
        ProcessBuilder pb = new ProcessBuilder();
        List<String> args = new ArrayList<>();

        args.add(workingDir + mvnw);
        args.add("--quiet");
        args.add("--file");
        args.add(workingDir);

        if (imagePlatforms != null) {
            args.add("-Dquarkus.jib.platforms=%s".formatted(imagePlatforms));
        }

        if (imageBuild) {
            args.add("-Dquarkus.container-image.build=true");
        }

        if (imagePush) {
            args.add("-Dquarkus.container-image.push=true");
        }

        if (ClusterType.OPENSHIFT.isEqualTo(clusterType)) {
            args.add("-Dquarkus.openshift.deploy=true");
        } else {
            args.add("-Dquarkus.kubernetes.deploy=true");
        }

        if (namespace != null) {
            args.add("-Dquarkus.kubernetes.namespace=%s".formatted(namespace));
        }

        args.add("package");
        pb.command(args.toArray(String[]::new));

        pb.inheritIO(); // run in foreground (with IO so logs are visible)
        Process p = pb.start();
        // wait for that process to exit as we run in foreground
        int exit = p.waitFor();
        if (exit != 0) {
            printer().println("Deployment failed!");
            return exit;
        }

        return 0;
    }

    private Integer buildSpringBoot(String workingDir) {
        printer().println("Building Spring Boot application ...");

        // TODO: implement SpringBoot project build
        return 0;
    }

    private Integer deploySpringBoot(String workingDir) {
        printer().println("Deploying to %s ...".formatted(Optional.ofNullable(clusterType)
                .map(StringHelper::capitalize).orElse("Kubernetes")));

        // TODO: implement SpringBoot Kubernetes deployment
        return 0;
    }

    private void configureFileWatch(DefaultCamelContext camelContext, KubernetesExport export, String workingDir)
            throws Exception {
        String watchDir = ".";
        FileFilter filter = null;
        if (filePaths != null && filePaths.length > 0) {
            watchDir = FileUtil.onlyPath(SourceScheme.onlyName(filePaths[0]));

            filter = pathname -> Arrays.stream(filePaths)
                    .map(FileUtil::stripPath)
                    .anyMatch(name -> name.equals(pathname.getName()));
        }

        FileWatcherResourceReloadStrategy reloadStrategy
                = new FileWatcherResourceReloadStrategy(watchDir);
        reloadStrategy.setResourceReload((name, resource) -> {
            printer().printf("Reloading project due to file change: %s%n", FileUtil.stripPath(name));
            int refresh = export.export();
            if (refresh == 0) {
                if (RuntimeType.quarkus == runtime) {
                    deployQuarkus(workingDir);
                } else if (RuntimeType.springBoot == runtime) {
                    deploySpringBoot(workingDir);
                }
            }
        });
        if (filter != null) {
            reloadStrategy.setFileFilter(filter);
        }
        camelContext.addService(reloadStrategy);
    }

    private String getProjectName() {
        if (image != null) {
            return KubernetesHelper.sanitize(StringHelper.beforeLast(image, ":"));
        }

        if (gav != null) {
            String[] ids = gav.split(":");
            if (ids.length > 1) {
                return KubernetesHelper.sanitize(ids[1]); // artifactId
            }
        }

        if (filePaths != null && filePaths.length > 0) {
            return KubernetesHelper.sanitize(FileUtil.onlyName(SourceScheme.onlyName(filePaths[0])));
        }

        throw new RuntimeCamelException(
                "Failed to resolve project name - please provide --gav, --image option or at least one source file");
    }

}
