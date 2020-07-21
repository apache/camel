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
package org.apache.camel.component.zookeeper;

import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.ZooKeeper.States;
import org.junit.Test;

public class ZookeeperConnectionManagerTest extends ZooKeeperTestSupport {

    @Test
    public void shouldWaitForConnection() {
        ZooKeeperConfiguration config = new ZooKeeperConfiguration();
        config.addZookeeperServer(getConnectionString());

        ZooKeeperComponent component = new ZooKeeperComponent(config);
        component.setConfiguration(config);
        component.setCamelContext(context);

        ZooKeeperEndpoint zep = new ZooKeeperEndpoint("zookeeper:someserver/this/is/a/path", component, config);

        ZooKeeperConnectionManager zkcm = new ZooKeeperConnectionManager(zep);
        ZooKeeper zk = zkcm.getConnection();
        zk.getState();
        assertEquals(States.CONNECTED, zk.getState());
    }
}
