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
package org.apache.camel.spring.cloud.zookeeper.support;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;

import org.apache.zookeeper.server.NIOServerCnxnFactory;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.apache.zookeeper.server.persistence.FileTxnSnapLog;
import org.springframework.util.SocketUtils;

public class ZookeeperServer {
    private NIOServerCnxnFactory connectionFactory;
    private ZooKeeperServer zkServer;

    public ZookeeperServer(File root) throws IOException, InterruptedException {
        zkServer = new ZooKeeperServer();

        File dataDir = new File(root, "log");
        File snapDir = new File(root, "data");
        FileTxnSnapLog ftxn = new FileTxnSnapLog(dataDir, snapDir);

        zkServer.setTxnLogFactory(ftxn);
        zkServer.setTickTime(1000);

        connectionFactory = new NIOServerCnxnFactory();
        connectionFactory.configure(new InetSocketAddress("localhost", SocketUtils.findAvailableTcpPort()), 0);
        connectionFactory.startup(zkServer);
    }

    public void shutdown() throws Exception {
        connectionFactory.shutdown();
        connectionFactory.join();

        zkServer.shutdown();

        while (zkServer.isRunning()) {
            zkServer.shutdown();
            Thread.sleep(100);
        }
    }

    public String connectString() {
        return "localhost:" + connectionFactory.getLocalPort();
    }
}
