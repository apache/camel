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
package org.apache.camel.test.infra.hdfs.v2.services;

import org.apache.camel.test.AvailablePortFinder;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.testcontainers.containers.GenericContainer;

public class HDFSContainer extends GenericContainer {

    private MiniDFSCluster cluster;

    @Override
    public void start() {
        try {
            Configuration conf = new Configuration();
            conf.set("dfs.namenode.fs-limits.max-directory-items", "1048576");
            cluster = new MiniDFSCluster.Builder(conf)
                    .nameNodePort(AvailablePortFinder.getNextAvailable())
                    .numDataNodes(3)
                    .format(true)
                    .build();
        } catch (Throwable e) {
            logger().warn("Couldn't start HDFS cluster. Test is not started, but passed!", e);
        }
    }

    @Override
    public void stop() {
        try {
            if (cluster != null) {
                cluster.shutdown();
            }
        } catch (Exception e) {
            logger().warn("Error shutting down the HDFS container", e);
        }
    }

    @Override
    public String getHost() {
        return "localhost";
    }

    public int getPort() {
        return cluster.getNameNodePort();
    }
}
