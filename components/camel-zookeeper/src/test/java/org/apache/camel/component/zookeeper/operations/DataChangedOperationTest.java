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

public class DataChangedOperationTest extends ZooKeeperTestSupport {

    @Test
    public void getsDataWhenNodeChanges() throws Exception {
        client.create("/datachanged", "this won't hurt a bit");
        ZooKeeper connection = getConnection();

        DataChangedOperation future = new DataChangedOperation(connection, "/datachanged", true);
        connection.getData("/datachanged", future, null);

        client.setData("/datachanged", "Really trust us", -1);
        assertArrayEquals("Really trust us".getBytes(), future.get().getResult());
    }

    @Test
    public void getsNotifiedWhenNodeIsDeleted() throws Exception {
        client.create("/existedButWasDeleted", "this won't hurt a bit");
        ZooKeeper connection = getConnection();

        DataChangedOperation future = new DataChangedOperation(connection, "/existedButWasDeleted", true);
        connection.getData("/existedButWasDeleted", future, null);

        client.delete("/existedButWasDeleted");
        assertEquals(null, future.get().getResult());
    }
}
