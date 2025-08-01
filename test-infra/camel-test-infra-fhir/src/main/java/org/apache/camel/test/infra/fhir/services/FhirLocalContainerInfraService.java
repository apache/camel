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
package org.apache.camel.test.infra.fhir.services;

import java.time.Duration;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.fhir.common.FhirProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

@InfraService(service = FhirInfraService.class,
              description = "HAPI FHIR RESTful test server",
              serviceAlias = { "fhir" })
public class FhirLocalContainerInfraService implements FhirInfraService, ContainerService<GenericContainer> {
    // needs https://github.com/hapifhir/hapi-fhir-jpaserver-starter/commit/54120f374eea5084634830d34c99a9137b22a310
    public static final String CONTAINER_NAME = "fhir";

    private static final Logger LOG = LoggerFactory.getLogger(FhirLocalContainerInfraService.class);

    private final GenericContainer container;

    public FhirLocalContainerInfraService() {
        this(LocalPropertyResolver.getProperty(
                FhirLocalContainerInfraService.class,
                FhirProperties.FHIR_CONTAINER));
    }

    public FhirLocalContainerInfraService(String imageName) {
        container = initContainer(imageName, CONTAINER_NAME);
    }

    public FhirLocalContainerInfraService(GenericContainer container) {
        this.container = container;
    }

    protected GenericContainer initContainer(String imageName, String containerName) {
        class FhirContainer extends GenericContainer<FhirContainer> {
            public FhirContainer(boolean fixedPort) {
                super(imageName);
                withNetworkAliases(containerName);
                if (fixedPort) {
                    addFixedExposedPort(FhirProperties.DEFAULT_SERVICE_PORT, FhirProperties.DEFAULT_SERVICE_PORT);
                } else {
                    withExposedPorts(FhirProperties.DEFAULT_SERVICE_PORT);
                }
                withStartupTimeout(Duration.ofMinutes(3L));
                withStartupAttempts(5);
                withEnv("hapi.fhir.allow_multiple_delete", "true");
                withEnv("hapi.fhir.fhir_version", "R4");
                withEnv("hapi.fhir.reuse_cached_search_results_millis", "-1");
                waitingFor(Wait.forListeningPort());
                waitingFor(Wait.forHttp("/fhir/metadata").withStartupTimeout(Duration.ofMinutes(3L)));
            }
        }

        return new FhirContainer(ContainerEnvironmentUtil.isFixedPort(this.getClass()));
    }

    @Override
    public void registerProperties() {
        System.setProperty(FhirProperties.SERVICE_BASE_URL, getServiceBaseURL());
        System.setProperty(FhirProperties.SERVICE_HOST, getHost());
        System.setProperty(FhirProperties.SERVICE_PORT, String.valueOf(getPort()));
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the FHIR container");
        container.start();

        registerProperties();
        LOG.info("FHIR instance running at {}", getServiceBaseURL());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the FHIR container");
        container.stop();
    }

    @Override
    public GenericContainer getContainer() {
        return container;
    }

    @Override
    public String getServiceBaseURL() {
        return String.format(
                "http://%s:%d/fhir",
                container.getHost(),
                container.getMappedPort(FhirProperties.DEFAULT_SERVICE_PORT));
    }

    @Override
    public String getHost() {
        return container.getHost();
    }

    @Override
    public Integer getPort() {
        return container.getMappedPort(FhirProperties.DEFAULT_SERVICE_PORT);
    }
}
