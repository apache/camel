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

package org.apache.camel.component.milo;

import org.apache.camel.component.milo.client.MiloClientCachingConnectionManager;
import org.apache.camel.component.milo.client.MiloClientConfiguration;
import org.apache.camel.component.milo.client.MiloClientConnection;
import org.apache.camel.component.milo.client.MonitorFilterConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MiloClientCachingConnectionManagerTest {

    private static final Logger LOG = LoggerFactory.getLogger(MiloClientCachingConnectionManagerTest.class);

    private MiloClientCachingConnectionManager instance;

    @BeforeEach
    public void setup(TestInfo testInfo) {
        final var displayName = testInfo.getDisplayName();
        LOG.info("********************************************************************************");
        LOG.info(displayName);
        LOG.info("********************************************************************************");
        instance = new MiloClientCachingConnectionManager();
    }

    @Test
    public void testCreateConnectionReuseConnection() {
        final MiloClientConfiguration configuration = new MiloClientConfiguration();

        MiloClientConnection connection1 = instance.createConnection(configuration, new MonitorFilterConfiguration());
        MiloClientConnection connection2 = instance.createConnection(configuration, new MonitorFilterConfiguration());

        Assertions.assertNotNull(connection1);
        Assertions.assertNotNull(connection2);
        Assertions.assertEquals(connection1, connection2);
    }

    @Test
    public void testReleaseConnectionNotLastConsumer() {
        final MiloClientConfiguration configuration = new MiloClientConfiguration();
        MiloClientConnection connection1 = instance.createConnection(configuration, new MonitorFilterConfiguration());
        instance.createConnection(configuration, new MonitorFilterConfiguration());

        instance.releaseConnection(connection1);

        MiloClientConnection connection3 = instance.createConnection(configuration, new MonitorFilterConfiguration());
        Assertions.assertEquals(connection1, connection3);
    }

    @Test
    public void testReleaseConnectionLastConsumer() {
        final MiloClientConfiguration configuration = new MiloClientConfiguration();
        MiloClientConnection connection1 = instance.createConnection(configuration, new MonitorFilterConfiguration());
        MiloClientConnection connection2 = instance.createConnection(configuration, new MonitorFilterConfiguration());

        instance.releaseConnection(connection1);
        instance.releaseConnection(connection2);

        MiloClientConnection connection3 = instance.createConnection(configuration, new MonitorFilterConfiguration());
        Assertions.assertNotSame(connection1, connection3);
    }
}
