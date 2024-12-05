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
import org.apache.camel.test.infra.openldap.common.OpenldapProperties;
import org.testcontainers.containers.GenericContainer;

public class OpenLdapContainer extends GenericContainer<OpenLdapContainer> {
    public static final String CONTAINER_NAME = "openldap";
    public static final int CONTAINER_PORT_LDAP = 389;
    public static final int CONTAINER_PORT_LDAP_OVER_SSL = 636;

    public OpenLdapContainer() {
        super(LocalPropertyResolver.getProperty(OpenldapLocalContainerInfraService.class,
                OpenldapProperties.OPENLDAP_CONTAINER));

        this.withExposedPorts(CONTAINER_PORT_LDAP, CONTAINER_PORT_LDAP_OVER_SSL)
                .withNetworkAliases(CONTAINER_NAME);
    }
}
