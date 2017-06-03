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

import org.apache.zookeeper.ZooKeeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>ZookeeperConnectionManager</code> is a simple class to manage
 * {@link ZooKeeper} connections.
 */
public class ZooKeeperConnectionManager {

    private static final Logger LOG = LoggerFactory.getLogger(ZooKeeperConnectionManager.class);

    private ZookeeperConnectionStrategy strategy;

    public ZooKeeperConnectionManager(ZooKeeperEndpoint endpoint) {
        strategy = new DefaultZookeeperConnectionStrategy(endpoint);
    }

    public ZooKeeper getConnection() {
        return strategy.getConnection().getZooKeeper();
    }

    private interface ZookeeperConnectionStrategy {
        ConnectionHolder getConnection();

        void shutdown();
    }

    private class DefaultZookeeperConnectionStrategy implements ZookeeperConnectionStrategy {
        private ConnectionHolder holder;
        private ZooKeeperConfiguration configuration;

        DefaultZookeeperConnectionStrategy(ZooKeeperEndpoint endpoint) {
            this.configuration = endpoint.getConfiguration();
            LOG.debug("Creating connection to ZooKeeper: {}", configuration);
            holder = new ConnectionHolder(configuration);
        }

        public ConnectionHolder getConnection() {
            return holder;
        }

        public void shutdown() {
            LOG.debug("Shutting down connection to ZooKeeper: {}", configuration);
            holder.closeConnection();
        }
    }

    public void shutdown() {
        strategy.shutdown();
    }

}
