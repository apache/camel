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
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.InternalServerErrorException;
import com.github.dockerjava.api.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.ContainerFetchException;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostArchitectureImagePullPolicyTest {

    private static final DockerImageName IMAGE = DockerImageName.parse("mirror.gcr.io/nats:2.15.3");
    private static final String NAME = IMAGE.asCanonicalNameString();

    @Test
    void normalizesJavaAndDockerArchitectures() {
        assertEquals("amd64", HostArchitectureImagePullPolicy.normalizeArch("amd64"));
        assertEquals("amd64", HostArchitectureImagePullPolicy.normalizeArch("x86_64"));
        assertEquals("arm64", HostArchitectureImagePullPolicy.normalizeArch("aarch64"));
        assertEquals("arm64", HostArchitectureImagePullPolicy.normalizeArch("arm64"));
        assertEquals("ppc64le", HostArchitectureImagePullPolicy.normalizeArch("ppc64le"));
        assertEquals("s390x", HostArchitectureImagePullPolicy.normalizeArch("s390x"));
        assertEquals("", HostArchitectureImagePullPolicy.normalizeArch(null));
    }

    @Test
    void checkIsEnabledByDefaultOnlyOnPowerAndZ() {
        assertTrue(HostArchitectureImagePullPolicy.isCheckedByDefault("ppc64le"));
        assertTrue(HostArchitectureImagePullPolicy.isCheckedByDefault("s390x"));
        assertFalse(HostArchitectureImagePullPolicy.isCheckedByDefault("amd64"));
        assertFalse(HostArchitectureImagePullPolicy.isCheckedByDefault("arm64"));
    }

    @Test
    void usesTheLocalImageWithTheRightArchitecture() {
        StubOperations operations = new StubOperations("ppc64le").withLocalImage("ppc64le");

        assertFalse(policy(operations, "ppc64le", true).shouldPull(IMAGE));

        assertTrue(operations.pulls.isEmpty());
        assertTrue(operations.removed.isEmpty());
    }

    @Test
    void pullsAMissingImageForTheHostPlatform() {
        StubOperations operations = new StubOperations("ppc64le");

        assertFalse(policy(operations, "ppc64le", true).shouldPull(IMAGE));

        assertEquals(List.of("linux/ppc64le"), operations.pulls);
        assertEquals("ppc64le", operations.localArch);
    }

    @Test
    void removesAndPullsAgainAnImageWithTheWrongArchitecture() {
        StubOperations operations = new StubOperations("s390x").withLocalImage("amd64");

        assertFalse(policy(operations, "s390x", true).shouldPull(IMAGE));

        assertEquals(List.of(NAME), operations.removed);
        assertEquals(List.of("linux/s390x"), operations.pulls);
        assertEquals("s390x", operations.localArch);
    }

    @Test
    void doesNothingWhenDisabled() {
        StubOperations operations = new StubOperations("amd64").withLocalImage("arm64");
        assertFalse(policy(operations, "amd64", false).shouldPull(IMAGE));

        StubOperations missing = new StubOperations("amd64");
        assertTrue(policy(missing, "amd64", false).shouldPull(IMAGE));

        assertTrue(operations.pulls.isEmpty());
        assertTrue(operations.removed.isEmpty());
        assertTrue(missing.pulls.isEmpty());
    }

    @Test
    void getsTheArchitectureFromTheDaemon() {
        StubOperations operations = new StubOperations("ppc64le");
        operations.daemonArch = "ppc64le";

        assertFalse(policy(operations, null, null).shouldPull(IMAGE));

        assertEquals(List.of("linux/ppc64le"), operations.pulls);
    }

    @Test
    void fallsBackToTheJvmArchitectureWhenTheDaemonOneIsUnknown() {
        StubOperations operations = new StubOperations(
                HostArchitectureImagePullPolicy
                        .normalizeArch(System.getProperty("os.arch")));
        operations.daemonArch = null;
        // the check is disabled by default except on ppc64le and s390x, force it to see the platform
        assertFalse(policy(operations, null, true).shouldPull(IMAGE));

        assertEquals(List.of("linux/" + HostArchitectureImagePullPolicy.normalizeArch(System.getProperty("os.arch"))),
                operations.pulls);
    }

    @Test
    void retriesWhenTheQuotaIsExceeded() {
        StubOperations operations = new StubOperations("ppc64le");
        operations.failures.add(new DockerClientException("Could not pull image: toomanyrequests: Quota exceeded"));
        operations.failures.add(new DockerClientException("Could not pull image: toomanyrequests: Quota exceeded"));

        assertFalse(policy(operations, "ppc64le", true).shouldPull(IMAGE));

        assertEquals(3, operations.pulls.size());
        assertEquals("ppc64le", operations.localArch);
    }

    @Test
    void retriesWhenThePullMadeNoProgress() {
        StubOperations operations = new StubOperations("ppc64le");
        operations.interruptions = 1;

        assertFalse(policy(operations, "ppc64le", true).shouldPull(IMAGE));

        assertEquals(2, operations.pulls.size());
        // the interrupted status must not be restored, it would make the following Docker calls and sleeps fail
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    void retriesWhenTheDaemonFails() {
        StubOperations operations = new StubOperations("ppc64le");
        operations.failures.add(new InternalServerErrorException("timeout"));

        assertFalse(policy(operations, "ppc64le", true).shouldPull(IMAGE));

        assertEquals(2, operations.pulls.size());
    }

    @Test
    void doesNotRetryWhenTheImageIsNotAvailableForThePlatform() {
        StubOperations operations = new StubOperations("ppc64le");
        operations.failures.add(new DockerClientException(
                "Could not pull image: no matching manifest for linux/ppc64le in the manifest list entries"));

        ContainerFetchException e = assertThrows(ContainerFetchException.class,
                () -> policy(operations, "ppc64le", true).shouldPull(IMAGE));

        assertEquals(1, operations.pulls.size());
        assertTrue(e.getMessage().contains("skipITs.ppc64le"), e.getMessage());
        assertNull(operations.localArch);
    }

    @Test
    void doesNotRetryOtherFailures() {
        StubOperations operations = new StubOperations("s390x");
        operations.failures.add(new NotFoundException("no such image"));

        assertThrows(ContainerFetchException.class, () -> policy(operations, "s390x", true).shouldPull(IMAGE));

        assertEquals(1, operations.pulls.size());
    }

    @Test
    void stopsRetryingAfterThePullTimeout() {
        StubOperations operations = new StubOperations("ppc64le");
        for (int i = 0; i < 100; i++) {
            operations.failures.add(new DockerClientException("toomanyrequests"));
        }
        HostArchitectureImagePullPolicy policy = new HostArchitectureImagePullPolicy(
                operations, "ppc64le", true, Duration.ofMillis(100), Duration.ofMillis(20));

        assertThrows(ContainerFetchException.class, () -> policy.shouldPull(IMAGE));

        assertTrue(operations.pulls.size() < 100);
    }

    @Test
    void failsWhenTheImageCannotBeInspectedAfterThePull() {
        StubOperations operations = new StubOperations("ppc64le");
        operations.pulledArch = null;

        assertThrows(ContainerFetchException.class, () -> policy(operations, "ppc64le", true).shouldPull(IMAGE));
    }

    @Test
    void removesTheImageWhenItHasTheWrongArchitectureAfterThePull() {
        StubOperations operations = new StubOperations("ppc64le");
        operations.pulledArch = "amd64";

        assertThrows(ContainerFetchException.class, () -> policy(operations, "ppc64le", true).shouldPull(IMAGE));

        assertEquals(List.of(NAME), operations.removed);
    }

    private static HostArchitectureImagePullPolicy policy(StubOperations operations, String hostArch, Boolean enabled) {
        return new HostArchitectureImagePullPolicy(
                operations, hostArch, enabled, Duration.ofSeconds(10), Duration.ofMillis(1));
    }

    private static final class StubOperations implements HostArchitectureImagePullPolicy.ImageOperations {

        final List<String> pulls = new ArrayList<>();
        final List<String> removed = new ArrayList<>();
        final Queue<RuntimeException> failures = new LinkedList<>();
        String daemonArch;
        String localArch;
        String pulledArch;
        int interruptions;

        StubOperations(String pulledArch) {
            this.pulledArch = pulledArch;
        }

        StubOperations withLocalImage(String arch) {
            this.localArch = arch;
            return this;
        }

        @Override
        public String daemonArch() {
            return daemonArch;
        }

        @Override
        public String inspectArch(String name) {
            return localArch;
        }

        @Override
        public void remove(String name) {
            removed.add(name);
            localArch = null;
        }

        @Override
        public void pull(DockerImageName imageName, String platform) throws InterruptedException {
            pulls.add(platform);
            if (interruptions > 0) {
                interruptions--;
                throw new InterruptedException("no progress");
            }
            RuntimeException failure = failures.poll();
            if (failure != null) {
                throw failure;
            }
            localArch = pulledArch;
        }
    }
}
