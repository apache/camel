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
package org.apache.camel.component.hazelcast;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.cluster.Address;
import com.hazelcast.config.ClassFilter;
import com.hazelcast.config.Config;
import com.hazelcast.config.JavaSerializationFilterConfig;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.core.Hazelcast;
import org.apache.camel.component.hazelcast.map.HazelcastMapComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.SimpleRegistry;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HazelcastUserConfigSerializationFilterWarningTest {

    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private final AbstractAppender appender
            = new AbstractAppender("SerializationFilterWarning", null, null, true, Property.EMPTY_ARRAY) {
                @Override
                public void append(LogEvent event) {
                    if (event.getLevel() == Level.WARN) {
                        warnings.add(event.getMessage().getFormattedMessage());
                    }
                }
            };
    private final SimpleRegistry registry = new SimpleRegistry();
    private DefaultCamelContext context;

    @BeforeEach
    void captureWarnings() {
        appender.start();
        componentLogger().addAppender(appender);
        // addAppender creates a non-additive logger config, which would hide this logger from the root appenders
        componentLogger().setAdditive(true);
    }

    @AfterEach
    void tearDown() {
        componentLogger().removeAppender(appender);
        appender.stop();
        if (context != null) {
            context.stop();
        }
        HazelcastClient.shutdownAll();
        Hazelcast.shutdownAll();
    }

    @Test
    void warnsOnceForUserConfigWithoutFilter() {
        try (AvailablePortFinder.Port port = AvailablePortFinder.find()) {
            Config config = memberConfig(port.getPort());
            registry.bind("userConfig", config);
            startContext(new HazelcastMapComponent());

            context.getEndpoint("hazelcast-map:cache-1?hazelcastConfig=#userConfig");
            context.getEndpoint("hazelcast-map:cache-2?hazelcastConfig=#userConfig");

            assertEquals(1, warningsFor(config.getInstanceName()));
        }
    }

    @Test
    void doesNotWarnForUserConfigWithFilter() {
        try (AvailablePortFinder.Port port = AvailablePortFinder.find()) {
            Config config = memberConfig(port.getPort());
            JavaSerializationFilterConfig filter = new JavaSerializationFilterConfig();
            filter.setWhitelist(new ClassFilter().addPrefixes("java.", "org.apache.camel."));
            config.getSerializationConfig().setJavaSerializationFilterConfig(filter);
            registry.bind("userConfig", config);
            startContext(new HazelcastMapComponent());

            context.getEndpoint("hazelcast-map:cache?hazelcastConfig=#userConfig");

            assertEquals(0, warningsFor(config.getInstanceName()));
        }
    }

    @Test
    void doesNotWarnForCamelBuiltConfig() {
        startContext(new HazelcastMapComponent());

        HazelcastDefaultEndpoint endpoint = (HazelcastDefaultEndpoint) context.getEndpoint("hazelcast-map:cache");

        assertEquals(0, warningsFor(endpoint.getHazelcastInstance().getName()));
    }

    @Test
    void warnsForUserClientConfigWithoutFilter() {
        try (AvailablePortFinder.Port port = AvailablePortFinder.find()) {
            Config memberConfig = memberConfig(port.getPort());
            Address member = Hazelcast.newHazelcastInstance(memberConfig).getCluster().getLocalMember().getAddress();

            ClientConfig clientConfig = new ClientConfig();
            clientConfig.setInstanceName("user-client-config-" + UUID.randomUUID());
            clientConfig.setClusterName(memberConfig.getClusterName());
            clientConfig.getNetworkConfig().addAddress(member.getHost() + ":" + member.getPort());
            clientConfig.getConnectionStrategyConfig().getConnectionRetryConfig().setClusterConnectTimeoutMillis(30000);
            registry.bind("userClientConfig", clientConfig);

            HazelcastMapComponent component = new HazelcastMapComponent();
            component.setHazelcastMode(HazelcastConstants.HAZELCAST_CLIENT_MODE);
            startContext(component);

            context.getEndpoint("hazelcast-map:cache?hazelcastConfig=#userClientConfig");

            assertEquals(1, warningsFor(clientConfig.getInstanceName()));
        }
    }

    private void startContext(HazelcastMapComponent component) {
        context = new DefaultCamelContext(registry);
        context.addComponent("hazelcast-map", component);
        context.start();
    }

    private long warningsFor(String instanceName) {
        return warnings.stream().filter(message -> message.contains(instanceName)).count();
    }

    private static Logger componentLogger() {
        return (Logger) LogManager.getLogger(HazelcastDefaultComponent.class);
    }

    private static Config memberConfig(int port) {
        Config config = new Config();
        config.setInstanceName("user-config-" + UUID.randomUUID());
        config.setClusterName("user-config-" + UUID.randomUUID());
        config.getNetworkConfig().setPort(port);
        JoinConfig join = config.getNetworkConfig().getJoin();
        join.getMulticastConfig().setEnabled(false);
        join.getTcpIpConfig().setEnabled(false);
        join.getAutoDetectionConfig().setEnabled(false);
        return config;
    }
}
