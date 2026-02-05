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

import java.lang.reflect.Method;
import java.util.Arrays;
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
    public static final String INFRA_PORT_PROPERTY = "camel.infra.port";
    private static final Logger LOG = LoggerFactory.getLogger(ContainerEnvironmentUtil.class);

    private static boolean dockerAvailable;
    private static boolean environmentCheckState;

    private ContainerEnvironmentUtil() {

    }

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
            Version version = DockerClientFactory.instance().client().versionCmd().exec();
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
     * Camel JBang and will use fixed default ports. This checks both direct and inherited interfaces.
     *
     * @param  cls the service class to check
     * @return     true if the service should use fixed ports, false for random ports
     */
    public static boolean isFixedPort(@SuppressWarnings("rawtypes") Class cls) {
        // Check the entire class hierarchy for InfraService interfaces
        Class<?> currentClass = cls;
        while (currentClass != null) {
            for (Class<?> i : currentClass.getInterfaces()) {
                if (i.getName().contains("InfraService")) {
                    LOG.debug("Service {} will use fixed ports (detected InfraService interface: {})",
                            cls.getSimpleName(), i.getSimpleName());
                    return true;
                }
            }
            currentClass = currentClass.getSuperclass();
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

    /**
     * Gets the configured port from system property, or returns the default port if not set or invalid.
     *
     * @param  defaultPort the default port to use if no valid port is configured
     * @return             the configured port or the default port
     */
    public static int getConfiguredPort(int defaultPort) {
        String portStr = System.getProperty(INFRA_PORT_PROPERTY);
        if (portStr != null && !portStr.isEmpty()) {
            try {
                return Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                LOG.warn("Invalid port value '{}', using default: {}", portStr, defaultPort);
            }
        }
        return defaultPort;
    }

    /**
     * Gets the configured port from system property for embedded services. Returns 0 (random port) if no port is
     * explicitly configured. Embedded services should use random ports by default for test isolation, and only use a
     * fixed port when explicitly configured.
     *
     * @return the configured port, or 0 for random port assignment
     */
    public static int getConfiguredPortOrRandom() {
        String portStr = System.getProperty(INFRA_PORT_PROPERTY);
        if (portStr != null && !portStr.isEmpty()) {
            try {
                return Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                LOG.warn("Invalid port value '{}', using random port", portStr);
            }
        }
        return 0;
    }

    /**
     * Configures port exposure for a single-port container based on fixed/random port mode.
     *
     * @param container   the container to configure
     * @param fixedPort   true to use fixed ports, false for random ports
     * @param defaultPort the default container port
     */
    public static void configurePort(GenericContainer<?> container, boolean fixedPort, int defaultPort) {
        configurePorts(container, fixedPort, PortConfig.primary(defaultPort));
    }

    /**
     * Configures port exposure for a container based on fixed/random port mode. Primary port uses configured value from
     * system property; secondary ports use defaults.
     *
     * @param container the container to configure
     * @param fixedPort true to use fixed ports, false for random ports
     * @param ports     the port configurations (primary and secondary)
     */
    public static void configurePorts(GenericContainer<?> container, boolean fixedPort, PortConfig... ports) {
        // Always expose the ports first - this is needed for wait strategies and port mapping
        Integer[] containerPorts = Arrays.stream(ports)
                .map(PortConfig::containerPort)
                .toArray(Integer[]::new);
        container.withExposedPorts(containerPorts);

        // If fixed port mode, also add the fixed port bindings
        if (fixedPort) {
            for (PortConfig port : ports) {
                int hostPort = port.primary() ? getConfiguredPort(port.containerPort()) : port.containerPort();
                invokeAddFixedExposedPort(container, hostPort, port.containerPort());
            }
        }
    }

    /**
     * Invokes the protected addFixedExposedPort method on a container using reflection. This method is protected in
     * GenericContainer to discourage use, but is necessary for fixed port scenarios like Camel JBang.
     *
     * @param container     the container to configure
     * @param hostPort      the host port to bind
     * @param containerPort the container port to expose
     */
    private static void invokeAddFixedExposedPort(GenericContainer<?> container, int hostPort, int containerPort) {
        try {
            Method method = GenericContainer.class.getDeclaredMethod("addFixedExposedPort", int.class, int.class);
            method.setAccessible(true);
            method.invoke(container, hostPort, containerPort);
        } catch (Exception e) {
            throw new RuntimeException("Failed to add fixed exposed port " + hostPort + " -> " + containerPort, e);
        }
    }

    /**
     * Configuration for a container port, indicating whether it's the primary port or a secondary port.
     *
     * @param containerPort the container port number
     * @param primary       true if this is the primary port (uses configured port from system property)
     */
    public record PortConfig(int containerPort, boolean primary) {
        /**
         * Creates a primary port configuration. The host port will be read from system property if configured.
         *
         * @param  port the container port
         * @return      a primary port configuration
         */
        public static PortConfig primary(int port) {
            return new PortConfig(port, true);
        }

        /**
         * Creates a secondary port configuration. The host port will use the same value as container port.
         *
         * @param  port the container port
         * @return      a secondary port configuration
         */
        public static PortConfig secondary(int port) {
            return new PortConfig(port, false);
        }
    }
}
