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
package org.apache.camel.component.jgroups.raft;

import java.util.UUID;

import org.apache.camel.impl.DefaultCamelContext;
import org.jgroups.protocols.raft.InMemoryLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Without channelProperties, the endpoint uses the raft.xml of the camel-jgroups-raft JAR, a sample for development and
 * tests, and logs a WARN when it starts. In the tests, the raft.xml of the test resources comes first on the classpath.
 */
class JGroupsRaftDefaultConfigWarningTest {

    private final String id = UUID.randomUUID().toString();
    private WarnCapture capture;
    private DefaultCamelContext context;

    @BeforeEach
    void start() {
        capture = new WarnCapture(JGroupsRaftEndpoint.class);
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
            InMemoryLog.logs.keySet().removeIf(key -> key.contains(id));
        }
    }

    @Test
    void warnsWithoutChannelProperties() {
        String clusterName = "default-" + id;

        // the endpoint starts (and creates its RaftHandle) when it is added to the started context
        JGroupsRaftEndpoint endpoint = context.getEndpoint("jgroups-raft:" + clusterName + "?raftId=learner-" + id,
                JGroupsRaftEndpoint.class);
        // the same endpoint again: it is started once
        context.getEndpoint("jgroups-raft:" + clusterName + "?raftId=learner-" + id, JGroupsRaftEndpoint.class);

        assertNotNull(endpoint.getResolvedRaftHandle());
        assertEquals(1, capture.count(clusterName), capture.warnings()::toString);
        assertEquals(1, capture.count(clusterName, "has no channelProperties", "raft.xml"), capture.warnings()::toString);
    }

    @Test
    void noWarningWithChannelPropertiesOfEndpoint() {
        String clusterName = "endpoint-" + id;

        context.getEndpoint("jgroups-raft:" + clusterName + "?raftId=learner-" + id + "&channelProperties=raftB.xml",
                JGroupsRaftEndpoint.class);

        assertEquals(0, capture.count(clusterName), capture.warnings()::toString);
    }

    @Test
    void noWarningWithChannelPropertiesOfComponent() {
        String clusterName = "component-" + id;
        JGroupsRaftComponent component = new JGroupsRaftComponent();
        component.setChannelProperties("raftB.xml");
        context.addComponent("my-jgroups-raft", component);

        context.getEndpoint("my-jgroups-raft:" + clusterName + "?raftId=learner-" + id, JGroupsRaftEndpoint.class);

        assertEquals(0, capture.count(clusterName), capture.warnings()::toString);
    }
}
