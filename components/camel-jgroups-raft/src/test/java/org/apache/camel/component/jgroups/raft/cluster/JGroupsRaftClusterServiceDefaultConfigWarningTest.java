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
package org.apache.camel.component.jgroups.raft.cluster;

import java.util.UUID;

import org.apache.camel.component.jgroups.raft.WarnCapture;
import org.jgroups.protocols.raft.InMemoryLog;
import org.jgroups.protocols.raft.RAFT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * With the default jgroupsConfig, the cluster service uses the raft.xml of the camel-jgroups-raft JAR, a sample for
 * development and tests, and logs a WARN when it creates its RaftHandle. In the tests, the raft.xml of the test
 * resources comes first on the classpath.
 */
class JGroupsRaftClusterServiceDefaultConfigWarningTest {

    private final String id = UUID.randomUUID().toString();
    private final RAFT.RoleChange listener = role -> {
    };
    private WarnCapture capture;

    @BeforeEach
    void startCapture() {
        capture = new WarnCapture(JGroupsRaftClusterService.class);
    }

    @AfterEach
    void stopCapture() {
        capture.close();
        InMemoryLog.logs.keySet().removeIf(key -> key.contains(id));
    }

    @Test
    void warnsWithDefaultConfig() throws Exception {
        // a raft id that is not a member of the configuration: a Learner, which does not take part in elections
        JGroupsRaftClusterService service = newService("default-" + id);

        service.connectView(listener);
        service.disconnectView(listener);

        assertEquals(1, capture.count("default-" + id, "raft.xml"), capture.warnings()::toString);
    }

    @Test
    void noWarningWithJgroupsConfig() throws Exception {
        JGroupsRaftClusterService service = newService("configured-" + id);
        service.setJgroupsConfig("raftABC.xml");

        service.connectView(listener);
        service.disconnectView(listener);

        assertEquals(0, capture.count("configured-" + id), capture.warnings()::toString);
    }

    private static JGroupsRaftClusterService newService(String raftId) {
        JGroupsRaftClusterService service = new JGroupsRaftClusterService();
        service.setId(raftId);
        service.setJgroupsClusterName("cluster-" + raftId);
        return service;
    }
}
