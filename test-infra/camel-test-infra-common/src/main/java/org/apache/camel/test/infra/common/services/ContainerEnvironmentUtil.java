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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;

public final class ContainerEnvironmentUtil {
    public static final String STARTUP_ATTEMPTS_PROPERTY = ".startup.attempts";
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

    public static void configureContainerStartup(GenericContainer<?> container, String property, int defaultValue) {
        int startupAttempts = Integer.valueOf(System.getProperty(property, String.valueOf(defaultValue)));
        container.setStartupAttempts(startupAttempts);
    }
}
