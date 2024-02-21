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
package org.apache.camel.test.infra.openldap.services;

import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.openldap.common.OpenldapProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;

public class OpenldapLocalContainerService implements OpenldapService, ContainerService<GenericContainer> {

    public static final String OPENLDAP_CONTAINER_PROPERTY = "openldap.container";
    public static final String CONTAINER_NAME = "openldap";
    public static final int CONTAINER_PORT_LDAP = 389;
    public static final int CONTAINER_PORT_LDAP_OVER_SSL = 636;

    private static final Logger LOG = LoggerFactory.getLogger(OpenldapLocalContainerService.class);

    private final GenericContainer container;

    public OpenldapLocalContainerService() {
        this(LocalPropertyResolver.getProperty(OpenldapLocalContainerService.class, OPENLDAP_CONTAINER_PROPERTY));
    }

    public OpenldapLocalContainerService(int port, int sslPort) {
        String imageName = System.getProperty(
                OpenldapProperties.OPENLDAP_CONTAINER,
                LocalPropertyResolver.getProperty(OpenldapLocalContainerService.class, OPENLDAP_CONTAINER_PROPERTY));

        container = initContainer(imageName, port, sslPort);
    }

    public OpenldapLocalContainerService(String imageName) {
        container = initContainer(imageName, null, null);
    }

    public OpenldapLocalContainerService(GenericContainer container) {
        this.container = container;
    }

    protected GenericContainer initContainer(String imageName, Integer port, Integer sslPort) {
        GenericContainer ret;

        if (port == null) {
            ret = new GenericContainer(imageName)
                    .withExposedPorts(CONTAINER_PORT_LDAP, CONTAINER_PORT_LDAP_OVER_SSL);
        } else {
            @SuppressWarnings("deprecation")
            FixedHostPortGenericContainer fixedPortContainer = new FixedHostPortGenericContainer(imageName)
                    .withFixedExposedPort(port, CONTAINER_PORT_LDAP);

            if (sslPort != null) {
                fixedPortContainer.withFixedExposedPort(sslPort, CONTAINER_PORT_LDAP_OVER_SSL);
            }

            ret = fixedPortContainer;
        }

        ret.withNetworkAliases(CONTAINER_NAME);

        return ret;
    }

    @Override
    public void registerProperties() {
        System.setProperty(OpenldapProperties.PORT_LDAP, String.valueOf(getPort()));
        System.setProperty(OpenldapProperties.PORT_LDAP_OVER_SSL, String.valueOf(getSslPort()));
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the Openldap container");
        container.start();

        registerProperties();
        LOG.info("Openldap instance running at {}", getPort());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the Openldap container");
        container.stop();
    }

    @Override
    public GenericContainer getContainer() {
        return container;
    }

    @Override
    public Integer getPort() {
        return container.getMappedPort(CONTAINER_PORT_LDAP);
    }

    @Override
    public Integer getSslPort() {
        return container.getMappedPort(CONTAINER_PORT_LDAP_OVER_SSL);
    }

    @Override
    public String getHost() {
        return container.getHost();
    }
}
