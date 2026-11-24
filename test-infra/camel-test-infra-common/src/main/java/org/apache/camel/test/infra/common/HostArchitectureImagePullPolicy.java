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

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.InternalServerErrorException;
import com.github.dockerjava.api.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.ContainerFetchException;
import org.testcontainers.images.ImagePullPolicy;
import org.testcontainers.images.TimeLimitedLoggedPullImageResultCallback;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.DockerLoggerFactory;
import org.testcontainers.utility.TestcontainersConfiguration;

/**
 * Testcontainers {@link ImagePullPolicy} making sure that the image used matches the architecture of the Docker daemon.
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
 * <li>removes a local image whose architecture differs from the daemon one, and logs it</li>
 * <li>pulls the image itself, explicitly for the daemon platform, without any fallback to another architecture</li>
 * <li>as Testcontainers does, retries the pull until {@code pull.timeout} (default 120s) when it is interrupted because
 * it made no progress, when the daemon replies with a server error or when the registry replies that a quota or rate
 * limit is exceeded ({@code toomanyrequests}). Any other failure, like an image that has no variant for the platform,
 * is not retried and fails immediately with a {@link ContainerFetchException}</li>
 * <li>checks the architecture of the image after the pull</li>
 * </ul>
 * The retries are done by the policy itself, they do not depend on the caller, for instance
 * {@link org.apache.camel.test.infra.common.services.TestServiceUtil#tryInitialize} is not used by the services created
 * with {@code createSingletonService()}.
 * <p>
 * The check is enabled by default on ppc64le and s390x daemons, and can be forced on or off with the
 * {@value #CHECK_PROPERTY} system property. When disabled, the behavior is the same as the Testcontainers default
 * policy: an image is only pulled if it is not available locally. The architecture is the one of the Docker daemon,
 * which can be different from the one of the JVM, for instance with a remote {@code DOCKER_HOST}. The JVM one is only
 * used when the daemon one cannot be determined.
 * <p>
 * <b>Scope:</b> this policy is registered through the {@code testcontainers.properties} file of this module, which is
 * in the jar. Testcontainers merges all the {@code testcontainers.properties} files of the classpath, so the policy
 * applies to all the containers started with Testcontainers when this module is on the classpath, not only to the ones
 * of {@code camel-test-infra}, for instance the Ryuk container or the ones of downstream projects. It is the same as
 * the Testcontainers default on amd64 and arm64. The {@code TESTCONTAINERS_PULL_POLICY} environment variable, the
 * {@code ~/.testcontainers.properties} file and a {@code testcontainers.properties} file in the module test resources
 * have precedence. If several jars of the classpath define {@code pull.policy}, which one is used is not defined.
 * <p>
 * <b>Concurrency:</b> Testcontainers uses one instance of the policy for the JVM, and {@code shouldPull} is called for
 * each container started, so it can be called concurrently for the same image. Docker operations are not atomic: two
 * callers can both find a wrong-architecture image and remove or pull it at the same time. This is tolerated: removing
 * and pulling are idempotent and a failed removal is only logged. Several JVMs can also share the same daemon. This
 * only happens if a wrong-architecture image is already in the cache.
 */
public class HostArchitectureImagePullPolicy implements ImagePullPolicy {

    public static final String CHECK_PROPERTY = "camel.test.infra.image.arch.check";

    private static final Logger LOG = LoggerFactory.getLogger(HostArchitectureImagePullPolicy.class);

    private static final Duration INITIAL_RETRY_DELAY = Duration.ofSeconds(1);
    private static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(15);

    private final ImageOperations operations;
    private final Boolean enabledOverride;
    private final Duration pullTimeout;
    private final Duration initialRetryDelay;

    private volatile String hostArch;

    public HostArchitectureImagePullPolicy() {
        this(new DockerClientImageOperations(), null, null,
             Duration.ofSeconds(TestcontainersConfiguration.getInstance().getImagePullTimeout()), INITIAL_RETRY_DELAY);
    }

    /**
     * @param operations        the operations on the Docker daemon
     * @param hostArch          the architecture of the daemon, or {@code null} to get it from the daemon
     * @param enabledOverride   if the check is enabled, or {@code null} to use the system property and the default of
     *                          the architecture
     * @param pullTimeout       the time during which a failed pull is retried
     * @param initialRetryDelay the delay before the first retry, doubled for each retry
     */
    HostArchitectureImagePullPolicy(ImageOperations operations, String hostArch, Boolean enabledOverride,
                                    Duration pullTimeout, Duration initialRetryDelay) {
        this.operations = operations;
        this.hostArch = hostArch == null ? null : normalizeArch(hostArch);
        this.enabledOverride = enabledOverride;
        this.pullTimeout = pullTimeout;
        this.initialRetryDelay = initialRetryDelay;
    }

    @Override
    public boolean shouldPull(DockerImageName imageName) {
        String name = imageName.asCanonicalNameString();
        String arch = hostArch();

        String localArch = operations.inspectArch(name);
        if (!isEnabled(arch)) {
            return localArch == null;
        }

        if (localArch != null) {
            if (arch.equals(normalizeArch(localArch))) {
                return false;
            }
            LOG.warn("Local image {} has architecture {} but the host is {}, removing it and pulling it again",
                    name, localArch, arch);
            removeImage(name);
        }

        pullForHostPlatform(imageName, arch);
        return false;
    }

    private boolean isEnabled(String arch) {
        if (enabledOverride != null) {
            return enabledOverride;
        }
        return Boolean.parseBoolean(System.getProperty(CHECK_PROPERTY, String.valueOf(isCheckedByDefault(arch))));
    }

    private String hostArch() {
        String arch = hostArch;
        if (arch == null) {
            arch = resolveHostArch();
            hostArch = arch;
        }
        return arch;
    }

    private String resolveHostArch() {
        String daemonArch = null;
        try {
            daemonArch = operations.daemonArch();
        } catch (RuntimeException e) {
            LOG.warn("Failed to get the architecture of the Docker daemon, using the one of the JVM: {}", e.getMessage());
        }
        if (daemonArch == null || daemonArch.isEmpty()) {
            return normalizeArch(System.getProperty("os.arch"));
        }
        return normalizeArch(daemonArch);
    }

    private void pullForHostPlatform(DockerImageName imageName, String arch) {
        String name = imageName.asCanonicalNameString();
        String platform = "linux/" + arch;
        LOG.info("Pulling image {} for platform {}", name, platform);

        Instant deadline = Instant.now().plus(pullTimeout);
        Duration delay = initialRetryDelay;
        while (true) {
            RuntimeException transientFailure;
            try {
                operations.pull(imageName, platform);
                break;
            } catch (InterruptedException e) {
                // Testcontainers interrupts the thread when a pull made no progress for pull.pause.timeout, and retries
                // the pull. The interrupted status is cleared when the exception is thrown, it must not be restored:
                // that would make the next Docker call, or the sleep of the caller, fail immediately.
                transientFailure = new ContainerFetchException("Pull of image " + name + " made no progress", e);
            } catch (InternalServerErrorException e) {
                transientFailure = e;
            } catch (DockerClientException e) {
                if (!isQuotaExceeded(e)) {
                    throw permanentFailure(name, platform, e);
                }
                transientFailure = e;
            } catch (RuntimeException e) {
                throw permanentFailure(name, platform, e);
            }

            if (Instant.now().plus(delay).isAfter(deadline)) {
                throw new ContainerFetchException(
                        "Failed to pull image " + name + " for platform " + platform + " during "
                                                  + pullTimeout.getSeconds() + "s: " + transientFailure.getMessage(),
                        transientFailure);
            }
            LOG.warn("Pulling image {} for platform {} failed, retrying in {}ms ({}s remaining): {}", name, platform,
                    delay.toMillis(), Duration.between(Instant.now(), deadline).getSeconds(),
                    transientFailure.getMessage());
            sleep(delay, name);
            delay = delay.multipliedBy(2).compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : delay.multipliedBy(2);
        }

        String pulledArch = operations.inspectArch(name);
        if (pulledArch == null) {
            throw new ContainerFetchException(
                    "Image " + name + " was pulled for platform " + platform + " but could not be inspected afterwards");
        }
        if (!arch.equals(normalizeArch(pulledArch))) {
            LOG.warn("Image {} has architecture {} after pulling it for platform {}, removing it", name, pulledArch,
                    platform);
            removeImage(name);
            throw new ContainerFetchException(
                    "Image " + name + " has architecture " + pulledArch + " but the host is " + arch);
        }
    }

    private static void sleep(Duration delay, String name) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ContainerFetchException("Interrupted while waiting to pull image " + name + " again", e);
        }
    }

    private static ContainerFetchException permanentFailure(String name, String platform, RuntimeException e) {
        // Do not let Testcontainers fall back to linux/amd64, this would leave an image that cannot run on this host
        String arch = platform.substring("linux/".length());
        String skipHint = isCheckedByDefault(arch)
                ? ", the tests needing it can be skipped with the skipITs." + arch
                  + " Maven property"
                : "";
        return new ContainerFetchException(
                "Failed to pull image " + name + " for platform " + platform
                                           + ", it is probably not available for this platform" + skipHint
                                           + ", set -D" + CHECK_PROPERTY
                                           + "=false to disable the architecture check: " + e.getMessage(),
                e);
    }

    private static boolean isQuotaExceeded(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("toomanyrequests") || lower.contains("too many requests") || lower.contains("quota exceeded")
                || lower.contains("rate limit");
    }

    private void removeImage(String name) {
        try {
            // Forced, to remove the image even if it is referenced by a stopped container: a wrong-architecture image
            // must not stay in the cache.
            operations.remove(name);
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

    /**
     * The operations done on the Docker daemon, to be able to test the policy without it.
     */
    interface ImageOperations {

        /**
         * @return the architecture of the Docker daemon, or {@code null} if it is not known
         */
        String daemonArch();

        /**
         * @return the architecture of the local image, or {@code null} if the image is not available locally
         */
        String inspectArch(String name);

        void remove(String name);

        void pull(DockerImageName imageName, String platform) throws InterruptedException;
    }

    private static final class DockerClientImageOperations implements ImageOperations {

        @Override
        public String daemonArch() {
            return DockerClientFactory.instance().getInfo().getArchitecture();
        }

        @Override
        public String inspectArch(String name) {
            try {
                return DockerClientFactory.lazyClient().inspectImageCmd(name).exec().getArch();
            } catch (NotFoundException e) {
                return null;
            }
        }

        @Override
        public void remove(String name) {
            DockerClient client = DockerClientFactory.lazyClient();
            client.removeImageCmd(name).withForce(true).exec();
        }

        @Override
        public void pull(DockerImageName imageName, String platform) throws InterruptedException {
            DockerClientFactory.lazyClient().pullImageCmd(imageName.getUnversionedPart())
                    .withTag(imageName.getVersionPart())
                    .withPlatform(platform)
                    .exec(new TimeLimitedLoggedPullImageResultCallback(
                            DockerLoggerFactory.getLogger(imageName.asCanonicalNameString())))
                    .awaitCompletion();
        }
    }
}
