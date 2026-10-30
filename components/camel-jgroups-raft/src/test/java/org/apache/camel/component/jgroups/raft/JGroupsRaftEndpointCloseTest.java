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

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.component.jgroups.raft.utils.NopStateMachine;
import org.apache.camel.impl.DefaultCamelContext;
import org.jgroups.JChannel;
import org.jgroups.protocols.raft.Log;
import org.jgroups.raft.RaftHandle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The endpoint closes the log of its own handle also when disconnecting or closing the channel fails.
 */
class JGroupsRaftEndpointCloseTest {

    private final AtomicBoolean channelClosed = new AtomicBoolean();
    private final AtomicBoolean logClosed = new AtomicBoolean();
    private DefaultCamelContext context;
    private JGroupsRaftEndpoint endpoint;

    @BeforeEach
    void startEndpoint() throws Exception {
        context = new DefaultCamelContext();
        context.start();
        RaftHandle handle = new RaftHandle(new FailingChannel(), new NopStateMachine()) {
            private final Log log = (Log) Proxy.newProxyInstance(Log.class.getClassLoader(), new Class<?>[] { Log.class },
                    (proxy, method, args) -> {
                        if ("close".equals(method.getName())) {
                            logClosed.set(true);
                        }
                        return null;
                    });

            @Override
            public Log log() {
                return log;
            }
        }.raftId("A");
        endpoint = new JGroupsRaftEndpoint(
                "jgroups-raft:JGroupsRaftEndpointCloseTest", "JGroupsRaftEndpointCloseTest",
                context.getComponent("jgroups-raft"), "A", null, new NopStateMachine(), handle);
        endpoint.start();
        // the endpoint now uses this handle as one it created itself, which it closes at stop
        endpoint.setRaftHandle(null);
    }

    @AfterEach
    void stopContext() {
        context.stop();
    }

    @Test
    void stopClosesTheLogWhenClosingTheChannelFails() {
        assertDoesNotThrow(() -> endpoint.stop());

        assertTrue(channelClosed.get(), "the channel close was attempted");
        assertTrue(logClosed.get(), "the log is closed although closing the channel failed");
    }

    @Test
    void lastDisconnectClosesTheHandleWhenDisconnectingTheChannelFails() throws Exception {
        endpoint.connect();
        endpoint.stop();
        assertFalse(logClosed.get(), "the handle is kept while a producer or consumer is connected");

        endpoint.disconnect();

        assertTrue(channelClosed.get(), "the channel close was attempted although disconnecting it failed");
        assertTrue(logClosed.get(), "the log is closed although disconnecting and closing the channel failed");
    }

    private final class FailingChannel extends JChannel {

        FailingChannel() throws Exception {
            super("raftABC.xml");
        }

        @Override
        public JChannel connect(String clusterName) {
            return this;
        }

        @Override
        public JChannel disconnect() {
            if (!channelClosed.get()) {
                throw new IllegalStateException("Simulated disconnect failure");
            }
            // the disconnect that close() itself does
            return super.disconnect();
        }

        @Override
        public void close() {
            channelClosed.set(true);
            super.close();
            throw new IllegalStateException("Simulated close failure");
        }
    }
}
