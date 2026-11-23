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
package org.apache.camel.test.infra.common;

import java.util.Locale;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.ContainerFetchException;
import org.testcontainers.images.ImagePullPolicy;
import org.testcontainers.images.TimeLimitedLoggedPullImageResultCallback;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.DockerLoggerFactory;

/**
 * Testcontainers {@link ImagePullPolicy} making sure that the image used matches the architecture of the host.
 * <p>
 * When pulling an image fails (for instance because the registry mirror replies {@code toomanyrequests}),
 * Testcontainers silently retries the pull for {@code linux/amd64}. On hosts with another architecture (ppc64le, s390x)
 * this leaves an image with the wrong architecture in the local cache, that is then reused by all subsequent runs and
 * fails with {@code exec format error}. This is reported to Testcontainers in
 * <a href="https://github.com/testcontainers/testcontainers-java/issues/12180">testcontainers-java#12180</a>, this
 * policy can be removed once it is fixed there.
 * <p>
 * When the check is enabled, this policy:
 * <ul>
 * <li>removes a local image whose architecture differs from the host one, and logs it</li>
 * <li>pulls the image itself, explicitly for the host platform, without any fallback to another architecture. If this
 * fails a {@link ContainerFetchException} is thrown, which is retried by
 * {@link org.apache.camel.test.infra.common.services.TestServiceUtil#tryInitialize}</li>
 * </ul>
 * The check is enabled by default on ppc64le and s390x hosts, and can be forced on or off with the
 * {@value #CHECK_PROPERTY} system property. When disabled, the behavior is the same as the Testcontainers default
 * policy: an image is only pulled if it is not available locally.
 * <p>
 * This policy is registered through the {@code testcontainers.properties} file of this module.
 * <p>
 * <b>Concurrency:</b> Testcontainers constructs a new policy instance per container and calls {@code shouldPull} once
 * per image, so concurrent calls for the same image are not expected in normal use. No additional synchronization is
 * applied.
 */
public class HostArchitectureImagePullPolicy implements ImagePullPolicy {

    public static final String CHECK_PROPERTY = "camel.test.infra.image.arch.check";

    private static final Logger LOG = LoggerFactory.getLogger(HostArchitectureImagePullPolicy.class);

    private final String hostArch;
    private final boolean enabled;

    public HostArchitectureImagePullPolicy() {
        this.hostArch = normalizeArch(System.getProperty("os.arch"));
        this.enabled = Boolean.parseBoolean(
                System.getProperty(CHECK_PROPERTY, String.valueOf(isCheckedByDefault(hostArch))));
    }

    @Override
    public boolean shouldPull(DockerImageName imageName) {
        DockerClient client = DockerClientFactory.lazyClient();
        String name = imageName.asCanonicalNameString();

        String localArch = inspectLocalArch(client, name);
        if (!enabled) {
            return localArch == null;
        }

        if (localArch != null) {
            if (hostArch.equals(normalizeArch(localArch))) {
                return false;
            }
            LOG.warn("Local image {} has architecture {} but the host is {}, removing it and pulling it again",
                    name, localArch, hostArch);
            removeImage(client, name);
        }

        pullForHostPlatform(client, imageName);
        return false;
    }

    private void pullForHostPlatform(DockerClient client, DockerImageName imageName) {
        String name = imageName.asCanonicalNameString();
        String platform = "linux/" + hostArch;
        LOG.info("Pulling image {} for platform {}", name, platform);
        try {
            client.pullImageCmd(imageName.getUnversionedPart())
                    .withTag(imageName.getVersionPart())
                    .withPlatform(platform)
                    .exec(new TimeLimitedLoggedPullImageResultCallback(DockerLoggerFactory.getLogger(name)))
                    .awaitCompletion();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ContainerFetchException("Interrupted while pulling image " + name + " for platform " + platform, e);
        } catch (RuntimeException e) {
            // Do not let Testcontainers fall back to linux/amd64, this would leave an image that cannot run on this host
            throw new ContainerFetchException(
                    "Failed to pull image " + name + " for platform " + platform + " (set -D" + CHECK_PROPERTY
                                              + "=false to disable the architecture check): " + e.getMessage(),
                    e);
        }

        String pulledArch = inspectLocalArch(client, name);
        if (pulledArch == null) {
            // Pull succeeded but inspect returned no architecture — treat as a failed pull so the caller retries
            throw new ContainerFetchException(
                    "Image " + name + " was pulled for platform " + platform
                                              + " but could not be inspected afterwards");
        }
        if (!hostArch.equals(normalizeArch(pulledArch))) {
            LOG.warn("Image {} has architecture {} after pulling it for platform {}, removing it", name, pulledArch,
                    platform);
            removeImage(client, name);
            throw new ContainerFetchException(
                    "Image " + name + " has architecture " + pulledArch + " but the host is " + hostArch);
        }
    }

    private static String inspectLocalArch(DockerClient client, String name) {
        try {
            InspectImageResponse image = client.inspectImageCmd(name).exec();
            return image.getArch();
        } catch (NotFoundException e) {
            return null;
        }
    }

    private static void removeImage(DockerClient client, String name) {
        try {
            // withForce(true) untags and removes the image even if its tag is referenced by a stopped container;
            // this is intentional so we do not leave a wrong-architecture image in the cache.
            client.removeImageCmd(name).withForce(true).exec();
        } catch (RuntimeException e) {
            LOG.warn("Failed to remove image {}: {}", name, e.getMessage());
        }
    }

    static boolean isCheckedByDefault(String normalizedHostArch) {
        return "ppc64le".equals(normalizedHostArch) || "s390x".equals(normalizedHostArch);
    }

    /**
     * Converts the value of the {@code os.arch} system property, or the architecture reported by Docker, to the
     * architecture name used by the container images (amd64, arm64, ppc64le, s390x).
     */
    static String normalizeArch(String arch) {
        if (arch == null) {
            return "";
        }
        switch (arch.toLowerCase(Locale.ROOT)) {
            case "x86_64":
            case "amd64":
                return "amd64";
            case "aarch64":
            case "arm64":
                return "arm64";
            default:
                return arch.toLowerCase(Locale.ROOT);
        }
    }
}
