/**
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
package org.apache.camel.component.zookeeper;

import org.apache.camel.CamelContext;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultMessage;
import org.apache.zookeeper.CreateMode;
import org.junit.Test;

import static org.apache.camel.component.zookeeper.ZooKeeperUtils.getCreateMode;
import static org.apache.camel.component.zookeeper.ZooKeeperUtils.getCreateModeFromString;

import static org.junit.Assert.assertEquals;

public class ZooKeeperUtilsTest {

    private CamelContext camelContext = new DefaultCamelContext();

    @Test
    public void testCreateModeExtraction() {
        assertEquals(CreateMode.EPHEMERAL, getCreateModeFromString("EPHEMERAL", CreateMode.EPHEMERAL));
        assertEquals(CreateMode.EPHEMERAL_SEQUENTIAL, getCreateModeFromString("EPHEMERAL_SEQUENTIAL", CreateMode.EPHEMERAL));
        assertEquals(CreateMode.PERSISTENT, getCreateModeFromString("PERSISTENT", CreateMode.EPHEMERAL));
        assertEquals(CreateMode.PERSISTENT_SEQUENTIAL, getCreateModeFromString("PERSISTENT_SEQUENTIAL", CreateMode.EPHEMERAL));
        assertEquals(CreateMode.EPHEMERAL, getCreateModeFromString("DOESNOTEXIST", CreateMode.EPHEMERAL));
    }
    
    @Test
    public void testCreateModeExtractionFromMessageHeader() {
        assertEquals(CreateMode.EPHEMERAL, testModeInMessage("EPHEMERAL", CreateMode.EPHEMERAL));
        assertEquals(CreateMode.EPHEMERAL_SEQUENTIAL, testModeInMessage("EPHEMERAL_SEQUENTIAL", CreateMode.EPHEMERAL));
        assertEquals(CreateMode.PERSISTENT, testModeInMessage("PERSISTENT", CreateMode.EPHEMERAL));
        assertEquals(CreateMode.PERSISTENT_SEQUENTIAL, testModeInMessage("PERSISTENT_SEQUENTIAL", CreateMode.EPHEMERAL));
        assertEquals(CreateMode.EPHEMERAL, testModeInMessage("DOESNOTEXIST", CreateMode.EPHEMERAL));
    }

    private CreateMode testModeInMessage(String mode, CreateMode defaultMode) {
        Message m = new DefaultMessage(camelContext);
        m.setHeader(ZooKeeperMessage.ZOOKEEPER_CREATE_MODE, mode);
        return getCreateMode(m, defaultMode);
    }
}
