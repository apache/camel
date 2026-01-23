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

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.test.infra.common.services.InfrastructureService;

/**
 * Test infra service for Openldap
 */
public interface OpenldapInfraService extends InfrastructureService {

    @Deprecated
    Integer getPort();

    @Deprecated
    Integer getSslPort();

    @Deprecated
    String getHost();

    default String host() {
        return getHost();
    }

    default int port() {
        return getPort();
    }

    default int sslPort() {
        return getSslPort();
    }

    default String ldapUrl() {
        return String.format("ldap://%s:%d", host(), port());
    }

    default String ldapContextFactory() {
        return "com.sun.jndi.ldap.LdapCtxFactory";
    }

    default String endpointUri() {
        return "ldap:ldapEnv";
    }

    default Map<String, String> beanProperties() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("camel.beans.ldapEnv", "#class:java.util.Hashtable");
        properties.put("camel.beans.ldapEnv[java.naming.factory.initial]", ldapContextFactory());
        properties.put("camel.beans.ldapEnv[java.naming.provider.url]", ldapUrl());
        return properties;
    }
}
