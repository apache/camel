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
package org.apache.camel.component.zookeeper.operations.integration;

import org.apache.camel.component.zookeeper.integration.ZooKeeperITSupport;
import org.apache.camel.component.zookeeper.operations.OperationResult;
import org.apache.camel.component.zookeeper.operations.SetDataOperation;
import org.apache.zookeeper.ZooKeeper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SetDataOperationIT extends ZooKeeperITSupport {

    private ZooKeeper connection;

    @BeforeEach
    public void setupConnection() {
        connection = getConnection();
    }

    @Test
    public void setData() throws Exception {
        client.create("/one", testPayload);
        SetDataOperation operation = new SetDataOperation(connection, "/one", "Updated".getBytes());
        OperationResult<byte[]> result = operation.get();
        verifyNodeContainsData("/one", "Updated".getBytes());
        assertEquals(1, result.getStatistics().getVersion());
    }

    @Test
    public void setSpecificVersionOfData() throws Exception {
        client.create("/two", testPayload);
        for (int x = 0; x < 10; x++) {
            byte[] payload = ("Updated_" + x).getBytes();
            updateDataOnNode("/two", payload, x, x + 1);
            verifyNodeContainsData("/two", payload);
        }
    }

    @Test
    public void setWithNull() throws Exception {
        client.create("/three", testPayload);
        updateDataOnNode("/three", null, -1, 1);
    }

    private void updateDataOnNode(String node, byte[] payload, int version, int expectedVersion) throws Exception {
        SetDataOperation operation = new SetDataOperation(connection, node, payload);
        operation.setVersion(version);
        OperationResult<byte[]> result = operation.get();
        assertNull(result.getException());
        verifyNodeContainsData(node, payload);
        assertEquals(expectedVersion, result.getStatistics().getVersion());
    }
}
