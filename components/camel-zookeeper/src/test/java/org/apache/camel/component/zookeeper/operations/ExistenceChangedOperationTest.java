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
package org.apache.camel.component.zookeeper.operations;

import org.apache.camel.component.zookeeper.ZooKeeperTestSupport;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooKeeper;
import org.junit.Test;

public class ExistenceChangedOperationTest extends ZooKeeperTestSupport {

    @Test
    public void getStatsWhenNodeIsCreated() throws Exception {
        String path = "/doesNotYetExist";
        ExistenceChangedOperation future = setupMonitor(path);

        client.create(path, "This is a test");
        assertEquals(path, future.get().getResult());
        assertNotNull(future.get().getStatistics());
    }

    @Test
    public void getsNotifiedWhenNodeIsDeleted() throws Exception {
        String path = "/soonToBeDeleted";
        client.create(path, "This is a test");
        ExistenceChangedOperation future = setupMonitor(path);

        client.delete(path);
        assertEquals(path, future.get().getResult());
        assertNull(future.get().getStatistics());
    }

    private ExistenceChangedOperation setupMonitor(String path) throws KeeperException, InterruptedException {
        ZooKeeper connection = getConnection();
        ExistenceChangedOperation future = new ExistenceChangedOperation(connection, path);
        connection.exists(path, future);
        return future;
    }
}
