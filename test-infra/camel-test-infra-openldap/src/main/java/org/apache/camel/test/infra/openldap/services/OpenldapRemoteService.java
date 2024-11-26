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

import org.apache.camel.test.infra.openldap.common.OpenldapProperties;

public class OpenldapRemoteService implements OpenldapService {

    public OpenldapRemoteService() {
    }

    public OpenldapRemoteService(String host, int port, int sslPort) {
        System.setProperty(OpenldapProperties.HOST, host);
        System.setProperty(OpenldapProperties.PORT_LDAP, String.valueOf(port));
        System.setProperty(OpenldapProperties.PORT_LDAP_OVER_SSL, String.valueOf(sslPort));
    }

    @Override
    public void registerProperties() {
        // NO-OP
    }

    @Override
    public void initialize() {
        registerProperties();
    }

    @Override
    public void shutdown() {
        // NO-OP
    }

    @Override
    public Integer getPort() {
        return getPort(OpenldapProperties.PORT_LDAP);
    }

    @Override
    public Integer getSslPort() {
        return getPort(OpenldapProperties.PORT_LDAP_OVER_SSL);
    }

    @Override
    public String getHost() {
        return System.getProperty(OpenldapProperties.HOST);
    }

    private Integer getPort(String prop) {
        String value = System.getProperty(prop);
        return Integer.valueOf(value);
    }
}
