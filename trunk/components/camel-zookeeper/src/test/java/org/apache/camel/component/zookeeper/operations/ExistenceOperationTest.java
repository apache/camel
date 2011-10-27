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

public class ExistenceOperationTest extends ZooKeeperTestSupport {

    @Test
    public void okWhenNodeMustExistAndDoesExists() throws Exception {
        client.create("/ergosum", "not a figment of your imagination");
        ZooKeeper connection = getConnection();

        ExistsOperation exists = new ExistsOperation(connection, "/ergosum");
        assertTrue(exists.get().isOk());
    }

    @Test
    public void notOkWhenNodeMustExistButDoesNotExist() throws Exception {
        ZooKeeper connection = getConnection();
        ExistsOperation exists = new ExistsOperation(connection, "/figment");
        assertFalse(exists.get().isOk());
    }

    @Test
    public void okWhenNodeMustNotExistAndDoesNotExists() throws Exception {
        ZooKeeper connection = getConnection();
        ExistsOperation exists = new ExistsOperation(connection, "/figment", false);
        assertTrue(exists.get().isOk());
    }

    @Test
    public void notOkWhenNodeMustExistButDoesExist() throws Exception {
        client.create("/i-exist-too", "not a figment of your imagination");
        ZooKeeper connection = getConnection();

        ExistsOperation exists = new ExistsOperation(connection, "/i-exist-too", false);
        assertFalse(exists.get().isOk());
    }
}
