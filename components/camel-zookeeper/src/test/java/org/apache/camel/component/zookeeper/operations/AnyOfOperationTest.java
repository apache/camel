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

import org.apache.camel.component.zookeeper.ZooKeeperTestSupport;
import org.apache.zookeeper.ZooKeeper;
import org.junit.Test;

public class AnyOfOperationTest extends ZooKeeperTestSupport {

    @Test
    public void testExistsOrWaitsWhenNodeExists() throws Exception {
        String node = "/cogito";
        client.create(node, "ergo sum");
        AnyOfOperations operation = getExistsOrWaitOperation(node);
        assertEquals(node, operation.get().getResult());
    }

    @Test
    public void testExistsOrWaitsWhenNodeDoesNotExist() throws Exception {
        String node = "/chapter-one";
        AnyOfOperations operation = getExistsOrWaitOperation(node);
        Thread.sleep(1000);
        client.create(node, "I am born");
        assertEquals(node, operation.get().getResult());
    }

    private AnyOfOperations getExistsOrWaitOperation(String node) {
        ZooKeeper connection = getConnection();
        AnyOfOperations operation = new AnyOfOperations(node, new ExistsOperation(connection, node),
                                                        new ExistenceChangedOperation(connection, node));
        return operation;
    }
}
