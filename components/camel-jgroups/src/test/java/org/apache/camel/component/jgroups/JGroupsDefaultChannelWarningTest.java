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
package org.apache.camel.component.jgroups;

import java.util.UUID;

import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Without channel and channelProperties, the endpoint uses the default udp.xml of the JGroups JAR, meant for
 * development and tests, and logs a WARN when it starts.
 */
class JGroupsDefaultChannelWarningTest {

    private final String id = UUID.randomUUID().toString();
    private WarnCapture capture;
    private DefaultCamelContext context;

    @BeforeEach
    void start() {
        capture = new WarnCapture(JGroupsEndpoint.class);
        context = new DefaultCamelContext();
        context.disableJMX();
        context.start();
    }

    @AfterEach
    void stop() {
        try {
            context.stop();
        } finally {
            capture.close();
        }
    }

    @Test
    void warnsWithoutChannelProperties() {
        String clusterName = "default-" + id;

        // the endpoint starts (and creates its channel, without connecting it) when it is added to the started context
        JGroupsEndpoint endpoint = context.getEndpoint("jgroups:" + clusterName, JGroupsEndpoint.class);
        // the same endpoint again: it is started once
        context.getEndpoint("jgroups:" + clusterName, JGroupsEndpoint.class);

        assertNotNull(endpoint.getResolvedChannel());
        assertEquals(1, capture.count(clusterName), capture.warnings()::toString);
        assertEquals(1, capture.count(clusterName, "udp.xml"), capture.warnings()::toString);
    }

    @Test
    void noWarningWithChannelPropertiesOfEndpoint() {
        String clusterName = "endpoint-" + id;

        context.getEndpoint("jgroups:" + clusterName + "?channelProperties=udp.xml", JGroupsEndpoint.class);

        assertEquals(0, capture.count(clusterName), capture.warnings()::toString);
    }

    @Test
    void noWarningWithChannelPropertiesOfComponent() {
        String clusterName = "component-" + id;
        JGroupsComponent component = new JGroupsComponent();
        component.setChannelProperties("udp.xml");
        context.addComponent("my-jgroups", component);

        context.getEndpoint("my-jgroups:" + clusterName, JGroupsEndpoint.class);

        assertEquals(0, capture.count(clusterName), capture.warnings()::toString);
    }
}
