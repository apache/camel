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

import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.openldap.common.OpenldapProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenldapLocalContainerService implements OpenldapService, ContainerService<OpenLdapContainer> {
    public static final int CONTAINER_PORT_LDAP = 389;
    public static final int CONTAINER_PORT_LDAP_OVER_SSL = 636;

    private static final Logger LOG = LoggerFactory.getLogger(OpenldapLocalContainerService.class);

    private final OpenLdapContainer container;

    public OpenldapLocalContainerService() {
        container = new OpenLdapContainer();
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
    public OpenLdapContainer getContainer() {
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
