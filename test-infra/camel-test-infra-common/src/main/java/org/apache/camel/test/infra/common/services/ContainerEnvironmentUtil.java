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

package org.apache.camel.test.infra.common.services;

import java.util.List;
import java.util.Objects;

import com.github.dockerjava.api.model.Version;
import com.github.dockerjava.api.model.VersionComponent;
import org.apache.camel.spi.annotations.InfraService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;

public final class ContainerEnvironmentUtil {
    public static final String STARTUP_ATTEMPTS_PROPERTY = ".startup.attempts";
    private static final Logger LOG = LoggerFactory.getLogger(ContainerEnvironmentUtil.class);

    private static boolean dockerAvailable;
    private static boolean environmentCheckState;

    private ContainerEnvironmentUtil() {}

    public static synchronized boolean isDockerAvailable() {
        if (!environmentCheckState) {
            try {
                dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
                if (!dockerAvailable) {
                    LOG.warn("Docker environment is not available");
                }
            } catch (Exception e) {
                LOG.error("Failed to evaluate whether the docker environment is available: {}", e.getMessage(), e);
                LOG.warn("Turning off container-based tests because docker does not seem to be working correctly");
                dockerAvailable = false;
            }

            environmentCheckState = true;
        }

        return dockerAvailable;
    }

    public static boolean isPodman() {
        try {
            Version version =
                    DockerClientFactory.instance().client().versionCmd().exec();
            List<VersionComponent> components = version.getComponents();
            if (components != null) {
                return components.stream()
                        .map(VersionComponent::getName)
                        .filter(Objects::nonNull)
                        .map(String::toLowerCase)
                        .anyMatch(name -> name.contains("podman"));
            }
        } catch (Exception e) {
            LOG.warn("Failed to determine container engine type", e);
        }

        return false;
    }

    public static void configureContainerStartup(GenericContainer<?> container, String property, int defaultValue) {
        int startupAttempts = Integer.valueOf(System.getProperty(property, String.valueOf(defaultValue)));
        container.setStartupAttempts(startupAttempts);
    }

    /**
     * Determines if a service class should use fixed ports (for Camel JBang compatibility) or random ports (for
     * testcontainer isolation).
     *
     * Services implementing an interface with "InfraService" in the name are considered to be intended for use with
     * Camel JBang and will use fixed default ports.
     *
     * @param  cls the service class to check
     * @return     true if the service should use fixed ports, false for random ports
     */
    public static boolean isFixedPort(@SuppressWarnings("rawtypes") Class cls) {
        for (Class<?> i : cls.getInterfaces()) {
            if (i.getName().contains("InfraService")) {
                LOG.debug(
                        "Service {} will use fixed ports (detected InfraService interface: {})",
                        cls.getSimpleName(),
                        i.getSimpleName());
                return true;
            }
        }

        LOG.debug("Service {} will use random ports (no InfraService interface detected)", cls.getSimpleName());
        return false;
    }

    public static String containerName(Class cls) {
        InfraService annotation = findAnnotation(cls);
        String name = null;
        if (annotation != null) {
            name = "camel-" + annotation.serviceAlias()[0];
            if (annotation.serviceImplementationAlias().length > 0) {
                name += "-" + annotation.serviceImplementationAlias()[0];
            }
        } else {
            LOG.warn("InfraService annotation not Found to determine container name alias.");
        }
        return name;
    }

    private static InfraService findAnnotation(Class cls) {
        InfraService annotation = (InfraService) cls.getAnnotation(InfraService.class);
        Class targetClass = cls;
        while (annotation == null && targetClass.getSuperclass() != null) {
            targetClass = targetClass.getSuperclass();
            annotation = (InfraService) targetClass.getAnnotation(InfraService.class);
            if (annotation != null) {
                break;
            }
        }
        return annotation;
    }
}
