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
import org.apache.zookeeper.ZooKeeper;
import org.junit.Test;

public class ChildrenChangedOperationTest extends ZooKeeperTestSupport {

    @Test
    public void getsListingWhenNodeIsCreated() throws Exception {
        String path = "/parent";
        client.createPersistent(path, null);

        ZooKeeper connection = getConnection();
        ChildrenChangedOperation future = new ChildrenChangedOperation(connection, path);
        connection.getChildren(path, future, null);

        client.createPersistent(path + "/child1", null);
        assertEquals(createChildListing("child1"), future.get().getResult());
    }

    @Test
    public void getsNotifiedWhenNodeIsDeleted() throws Exception {
        String path = "/parent2";
        client.createPersistent(path, null);
        client.createPersistent(path + "/child1", null);

        ZooKeeper connection = getConnection();
        ChildrenChangedOperation future = new ChildrenChangedOperation(connection, path);
        connection.getChildren(path, future, null);

        client.delete(path + "/child1");
        assertEquals(createChildListing(), future.get().getResult());
    }

    @Test
    public void getsNoListingWhenOnlyChangeIsRequired() throws Exception {
        String path = "/parent3";
        client.createPersistent(path, null);

        ZooKeeper connection = getConnection();
        ChildrenChangedOperation future = new ChildrenChangedOperation(connection, path, false);
        connection.getChildren(path, future, null);

        client.createPersistent(path + "/child3", null);
        assertEquals(null, future.get());
    }
}
