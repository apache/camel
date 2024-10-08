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
package org.apache.camel.component.nats;

import org.junit.Assert;
import org.junit.jupiter.api.Test;

/**
 * Test NATS consumer stop happens cleanly. See https://issues.apache.org/jira/browse/CAMEL-15834.
 */
public class NatsSplitServersMultiprotocolTest {
    @Test
    public void testMultiServers() throws Exception {
        NatsConfiguration conf = new NatsConfiguration();
        conf.setServers("nats://localhost:1234,localhost:6574,ws://localhost:2134");
        String serv = conf.splitServers();
        Assert.assertEquals(serv, "nats://localhost:1234,nats://localhost:6574,ws://localhost:2134");
    }

    @Test
    public void testWssProtocols() throws Exception {
        NatsConfiguration conf = new NatsConfiguration();
        conf.setServers("wss://localhost:1234,wss://localhost:6574,wss://localhost:2134");
        String serv = conf.splitServers();
        Assert.assertEquals(serv, "wss://localhost:1234,wss://localhost:6574,wss://localhost:2134");
    }

    @Test
    public void testWsProtocols() throws Exception {
        NatsConfiguration conf = new NatsConfiguration();
        conf.setServers("ws://localhost:1234,ws://localhost:6574,ws://localhost:2134");
        String serv = conf.splitServers();
        Assert.assertEquals(serv, "ws://localhost:1234,ws://localhost:6574,ws://localhost:2134");
    }

    @Test
    public void testNoProtocols() throws Exception {
        NatsConfiguration conf = new NatsConfiguration();
        conf.setServers("localhost:1234,localhost:6574,localhost:2134");
        String serv = conf.splitServers();
        Assert.assertEquals(serv, "nats://localhost:1234,nats://localhost:6574,nats://localhost:2134");
    }
}
