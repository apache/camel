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
package org.apache.camel.component.zookeeper.operations;

import java.util.Collections;
import java.util.List;

import org.apache.camel.component.zookeeper.ZooKeeperTestSupport;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooDefs.Perms;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.ACL;
import org.junit.Before;
import org.junit.Test;

public class CreateOperationTest extends ZooKeeperTestSupport {

    private ZooKeeper connection;

    @Before
    public void setupConnection() {
        connection = getConnection();
    }

    @Test
    public void createBasic() throws Exception {
        CreateOperation create = new CreateOperation(connection, "/one");

        OperationResult<String> result = create.get();
        assertEquals("/one", result.getResult());

        verifyNodeContainsData("/one", null);
    }

    @Test
    public void createBasicSubPath() throws Exception {
        CreateOperation create = new CreateOperation(connection, "/sub/sub2");

        OperationResult<String> result = create.get();
        assertEquals("/sub/sub2", result.getResult());

        verifyNodeContainsData("/sub/sub2", null);
    }

    @Test
    public void createBasicSubPathWithData() throws Exception {
        CreateOperation create = new CreateOperation(connection, "/sub/sub3");
        create.setData(testPayload.getBytes());

        OperationResult<String> result = create.get();
        assertEquals("/sub/sub3", result.getResult());

        verifyNodeContainsData("/sub/sub3", testPayloadBytes);
    }

    @Test
    public void createBasicWithData() throws Exception {
        CreateOperation create = new CreateOperation(connection, "/two");
        create.setData(testPayload.getBytes());

        OperationResult<String> result = create.get();

        assertEquals("/two", result.getResult());
        verifyNodeContainsData("/two", testPayloadBytes);
    }

    @Test
    public void createSequencedNodeToTestCreateMode() throws Exception {
        CreateOperation create = new CreateOperation(connection, "/three");
        create.setData(testPayload.getBytes());
        create.setCreateMode(CreateMode.EPHEMERAL_SEQUENTIAL);

        OperationResult<String> result = create.get();
        String out = result.getResult();

        assertTrue(out.startsWith("/three"));
        verifyNodeContainsData(out, testPayloadBytes);
    }

    @Test
    public void createNodeWithSpecificAccess() throws Exception {
        CreateOperation create = new CreateOperation(connection, "/four");
        create.setData(testPayload.getBytes());
        List<ACL> perms = Collections.singletonList(new ACL(Perms.CREATE, Ids.ANYONE_ID_UNSAFE));
        create.setPermissions(perms);

        OperationResult<String> result = create.get();
        assertEquals("/four", result.getResult());

        verifyAccessControlList("/four", perms);
    }
}
