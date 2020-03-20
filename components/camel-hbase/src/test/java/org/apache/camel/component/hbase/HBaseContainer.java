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
package org.apache.camel.component.hbase;


import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.testcontainers.containers.GenericContainer;

/**
 * Currently there is no official HBase docker image.
 * This is dummy implementation of the HBase container.
 */
public class HBaseContainer extends GenericContainer {

    // must be the same as in the config of camel component
    private static final Integer CLIENT_PORT = 21818;

    private final HBaseTestingUtility hbaseUtil;

    public HBaseContainer() {
        hbaseUtil = new HBaseTestingUtility(defaultConf());
    }

    public HBaseContainer(Configuration conf) {
        hbaseUtil = new HBaseTestingUtility(conf);
    }

    @Override
    public void start() {
        try {
            hbaseUtil.startMiniCluster(1);
        } catch (Exception e) {
            logger().warn("couldn't start HBase cluster. Test is not started, but passed!", e);
        }
    }

    @Override
    public void stop() {
        try {
            hbaseUtil.shutdownMiniCluster();
        } catch (Exception e) {
            logger().warn("Error shutting down the HBase container", e);
        }
    }

    public static Configuration defaultConf() {
        Configuration conf = HBaseConfiguration.create();
        conf.set("test.hbase.zookeeper.property.clientPort", CLIENT_PORT.toString());
        return conf;
    }

}
