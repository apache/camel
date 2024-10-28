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
package org.apache.camel.test.infra.artemis.services;

import java.util.HashSet;
import java.util.Set;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.CoreAddressConfiguration;
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager;
import org.apache.activemq.artemis.spi.core.security.jaas.InVMLoginModule;
import org.apache.camel.test.infra.artemis.common.ArtemisProperties;

import static org.junit.jupiter.api.Assertions.fail;

public class ArtemisAMQPService extends AbstractArtemisEmbeddedService {

    private String brokerURL;
    private int amqpPort;

    @Override
    protected Configuration configure(Configuration artemisConfiguration, int port, int brokerId) {
        amqpPort = port;
        String sslEnabled = System.getProperty(ArtemisProperties.ARTEMIS_SSL_ENABLED, "false");
        String keyStorePath = System.getProperty(ArtemisProperties.ARTEMIS_SSL_KEYSTORE_PATH, "");
        String keyStorePassword = System.getProperty(ArtemisProperties.ARTEMIS_SSL_KEYSTORE_PASSWORD, "");
        String trustStorePath = System.getProperty(ArtemisProperties.ARTEMIS_SSL_TRUSTSTORE_PATH, "");
        String trustStorePassword = System.getProperty(ArtemisProperties.ARTEMIS_SSL_TRUSTSTORE_PASSWORD, "");
        brokerURL = "tcp://0.0.0.0:" + amqpPort
                    + "?tcpSendBufferSize=1048576;tcpReceiveBufferSize=1048576;protocols=AMQP;useEpoll=true;amqpCredits=1000;amqpMinCredits=300"
                    + String.format(
                            ";sslEnabled=%s;keyStorePath=%s;keyStorePassword=%s;trustStorePath=%s;trustStorePassword=%s",
                            sslEnabled, keyStorePath, keyStorePassword, trustStorePath, trustStorePassword);

        AddressSettings addressSettings = new AddressSettings();
        addressSettings.setAddressFullMessagePolicy(AddressFullMessagePolicy.FAIL);

        // Disable auto create address to make sure that topic name is correct without prefix
        try {
            artemisConfiguration.addAcceptorConfiguration("amqp", brokerURL);
        } catch (Exception e) {
            LOG.warn(e.getMessage(), e);
            fail("AMQP acceptor cannot be configured");
        }
        artemisConfiguration.setPersistenceEnabled(false);
        artemisConfiguration.addAddressesSetting("#", addressSettings);
        artemisConfiguration.setSecurityEnabled(
                "true".equalsIgnoreCase(System.getProperty(ArtemisProperties.ARTEMIS_AUTHENTICATION_ENABLED)));
        if (artemisConfiguration.isSecurityEnabled()) {
            SecurityConfiguration sc = new SecurityConfiguration();
            String user = System.getProperty(ArtemisProperties.ARTEMIS_USERNAME, "camel");
            String pw = System.getProperty(ArtemisProperties.ARTEMIS_PASSWORD, "rider");
            sc.addUser(user, pw);
            sc.addRole(user, "ALLOW_ALL");
            ActiveMQSecurityManager securityManager = new ActiveMQJAASSecurityManager(InVMLoginModule.class.getName(), sc);
            embeddedBrokerService.setSecurityManager(securityManager);

            // any user can have full control of generic topics
            String roleName = "ALLOW_ALL";
            Role role = new Role(roleName, true, true, true, true, true, true, true, true, true, true, false, false);
            Set<Role> roles = new HashSet<>();
            roles.add(role);
            artemisConfiguration.putSecurityRoles("#", roles);
        }
        artemisConfiguration.setMaxDiskUsage(98);

        // Set explicit topic name
        CoreAddressConfiguration pingTopicConfig = new CoreAddressConfiguration();
        pingTopicConfig.setName("topic.ping");
        pingTopicConfig.addRoutingType(RoutingType.MULTICAST);

        artemisConfiguration.addAddressConfiguration(pingTopicConfig);

        return artemisConfiguration;
    }

    @Override
    public String serviceAddress() {
        return brokerURL;
    }

    @Override
    public int brokerPort() {
        return amqpPort;
    }
}
