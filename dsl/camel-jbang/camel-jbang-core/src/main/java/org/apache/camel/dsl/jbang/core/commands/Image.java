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
package org.apache.camel.dsl.jbang.core.commands;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.DockerDaemonImage;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.Jib;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.openshift.api.model.Build;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.OpenShiftConfig;
import io.fabric8.openshift.client.OpenShiftConfigBuilder;
import picocli.CommandLine;

@CommandLine.Command(name = "image", description = "Create Docker and OCI container images")
public class Image implements Callable<Integer> {

    private static final int LOG_TAIL_SIZE = 10;

    @CommandLine.Option(names = { "-h", "--help" }, usageHelp = true, description = "Display the help and sub-commands")
    private boolean helpRequested;
    @CommandLine.Option(names = { "-f", "--from" }, description = "Base Image", defaultValue = "gcr.io/distroless/java:11")
    private String from;
    @CommandLine.Option(names = { "-j", "--jar" }, description = "Jar file", defaultValue = "camel-runner.jar")
    private String jar;
    @CommandLine.Option(names = { "--push" }, description = "Push to the registry")
    private boolean push;
    @CommandLine.Option(names = { "-r", "--registry" }, description = "Registry image reference")
    private String registry;
    @CommandLine.Option(names = { "-u", "--username" }, description = "Registry username")
    private String username;
    @CommandLine.Option(names = { "-p", "--password" }, description = "Registry password (token)")
    private String password;
    @CommandLine.Option(names = { "--openshift" }, description = "Build in OpenShift")
    private boolean openshift;
    @CommandLine.Option(names = { "--build-config" }, description = "Build in OpenShift using BuildConfig")
    private boolean buildConfig;
    @CommandLine.Option(names = { "--server" }, description = "Master URL")
    private String server;
    @CommandLine.Option(names = { "--token" }, description = "Token")
    private String token;
    @CommandLine.Option(names = { "--namespace" }, description = "Namespace", defaultValue = "default")
    private String namespace;
    @CommandLine.Option(names = { "--name" }, description = "Application name", required = true)
    private String name;
    @CommandLine.Option(names = { "--version" }, description = "Application version (label)", required = true)
    private String version;
    @CommandLine.Option(names = { "--source-image" }, description = "Source image name (for OpenShift BuildConfig)",
                        defaultValue = "java:openjdk-11-ubi8")
    private String sourceImage;

    @Override
    public Integer call() throws Exception {
        File jarFile = Paths.get(jar).toFile();
        if (openshift && buildConfig) {
            buildInOpenshiftWithBuildConfig(jarFile);
        } else {
            String host = openshift ? getOpenshiftRegistryHost() : registry;
            String imageReference = host + "/" + namespace + "/" + name + ":" + version;
            Jib.from(from)
                    .addLayer(Arrays.asList(Paths.get(jar)), "/deployments/")
                    .setWorkingDirectory(AbsoluteUnixPath.get("/deployments"))
                    .setEntrypoint("java", "-jar", jarFile.getName())
                    .containerize(push ? getRegistry(imageReference) : getDockerImage());
        }
        return 0;
    }

    private String getOpenshiftRegistryHost() {
        OpenShiftConfig config
                = new OpenShiftConfigBuilder().withMasterUrl(server).withOauthToken(token).withNamespace(namespace)
                        .withTrustCerts(true).build();
        try (OpenShiftClient client = new DefaultOpenShiftClient(config)) {
            String host = client.routes().inNamespace("openshift-image-registry").list().getItems().get(0).getSpec().getHost();
            return host + "/" + namespace + "/" + name + ":" + version;
        }
    }

    private void buildInOpenshiftWithBuildConfig(File jarFile) throws InvalidImageReferenceException {
        System.out.println("Generating resources...");
        OpenShiftConfig config
                = new OpenShiftConfigBuilder().withMasterUrl(server).withOauthToken(token).withNamespace(namespace)
                        .withTrustCerts(true).build();
        try (OpenShiftClient client = new DefaultOpenShiftClient(config)) {
            ImageStream imageStream = KubernetesHelper.createImageStream(namespace, name, version);
            BuildConfig buildConfig
                    = KubernetesHelper.createBuildConfig(namespace, name, version, jarFile.getName(), sourceImage);
            System.out.println("Creating ImageStream...");
            client.imageStreams().createOrReplace(imageStream);
            System.out.println("Creating BuildConfig...");
            client.buildConfigs().createOrReplace(buildConfig);
            System.out.println("Creating Build...");
            Build build = client.buildConfigs()
                    .inNamespace(namespace)
                    .withName(buildConfig.getMetadata().getName())
                    .instantiateBinary()
                    .asFile(jarFile.getName())
                    .withTimeout(5, TimeUnit.MINUTES)
                    .fromFile(jarFile);
            while (isNew(build) || isPending(build) || isRunning(build)) {
                final String buildName = build.getMetadata().getName();
                Build updated = client.builds().withName(buildName).get();
                if (updated == null) {
                    throw new IllegalStateException("Build:" + build.getMetadata().getName() + " is no longer present!");
                } else if (updated.getStatus() == null) {
                    throw new IllegalStateException("Build:" + build.getMetadata().getName() + " has no status!");
                } else if (isNew(updated) || isPending(updated) || isRunning(updated)) {
                    build = updated;
                    try (LogWatch w
                            = client.builds().withName(buildName).withPrettyOutput().watchLog();
                         Reader reader = new InputStreamReader(w.getOutput())) {
                        display(reader);
                    } catch (IOException e) {
                        // This may happen if the LogWatch is closed while we are still reading.
                        // We shouldn't let the build fail, so let's log a warning and display last few lines of the log
                        System.out.println("Log stream closed, redisplaying last " + LOG_TAIL_SIZE + " entries:");
                        try {
                            display(client.builds().withName(buildName).tailingLines(LOG_TAIL_SIZE)
                                    .getLogReader());
                        } catch (IOException ex) {
                            // Let's ignore this.
                        }
                    }
                } else if (isComplete(updated)) {
                    break;
                } else if (isCancelled(updated)) {
                    throw new IllegalStateException("Build:" + buildName + " cancelled!");
                } else if (isFailed(updated)) {
                    throw new IllegalStateException(
                            "Build:" + buildName + " failed! " + updated.getStatus().getMessage());
                } else if (isError(updated)) {
                    throw new IllegalStateException(
                            "Build:" + buildName + " encountered error! " + updated.getStatus().getMessage());
                }
            }
        }
    }

    private Containerizer getDockerImage() throws InvalidImageReferenceException {
        String image = namespace + "/" + name + ":" + version;
        return Containerizer.to(DockerDaemonImage.named(image)).addEventHandler(LogEvent.class, getEventConsumer());
    }

    private Containerizer getRegistry(String imageReference) throws InvalidImageReferenceException {
        return Containerizer.to(
                RegistryImage.named(imageReference).addCredential(username, password))
                .addEventHandler(LogEvent.class, getEventConsumer());
    }

    private Consumer<LogEvent> getEventConsumer() {
        return event -> System.out.println(event.getLevel() + ": " + event.getMessage());
    }

    private static void display(Reader logReader) throws IOException {
        BufferedReader reader = new BufferedReader(logReader);
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            System.out.println(line);
        }
    }

    static boolean isNew(Build build) {
        return build != null && build.getStatus() != null
                && BuildStatus.New.name().equalsIgnoreCase(build.getStatus().getPhase());
    }

    static boolean isPending(Build build) {
        return build != null && build.getStatus() != null
                && BuildStatus.Pending.name().equalsIgnoreCase(build.getStatus().getPhase());
    }

    static boolean isRunning(Build build) {
        return build != null && build.getStatus() != null
                && BuildStatus.Running.name().equalsIgnoreCase(build.getStatus().getPhase());
    }

    static boolean isComplete(Build build) {
        return build != null && build.getStatus() != null
                && BuildStatus.Complete.name().equalsIgnoreCase(build.getStatus().getPhase());
    }

    static boolean isFailed(Build build) {
        return build != null && build.getStatus() != null
                && BuildStatus.Failed.name().equalsIgnoreCase(build.getStatus().getPhase());
    }

    static boolean isError(Build build) {
        return build != null && build.getStatus() != null
                && BuildStatus.Error.name().equalsIgnoreCase(build.getStatus().getPhase());
    }

    static boolean isCancelled(Build build) {
        return build != null && build.getStatus() != null
                && BuildStatus.Cancelled.name().equalsIgnoreCase(build.getStatus().getPhase());
    }

    public enum BuildStatus {

        New,
        Pending,
        Running,
        Complete,
        Failed,
        Error,
        Cancelled;

    }
}
