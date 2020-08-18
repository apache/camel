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

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ZooKeeperCuratorHelperTest {

    @Test
    public void testCreateCuratorRetryPolicy() throws Exception {
        ZooKeeperCuratorConfiguration configuration = new ZooKeeperCuratorConfiguration();

        configuration.setNodes("nodes1,node2,node3");
        configuration.setReconnectBaseSleepTime(10);
        configuration.setReconnectMaxRetries(3);
        configuration.setReconnectMaxSleepTime(50);
        configuration.setRetryPolicy(null);

        CuratorFramework curatorFramework = ZooKeeperCuratorHelper.createCurator(configuration);

        assertNotNull(curatorFramework);
        ExponentialBackoffRetry retryPolicy = (ExponentialBackoffRetry) curatorFramework.getZookeeperClient().getRetryPolicy();

        assertEquals(configuration.getReconnectBaseSleepTime(), retryPolicy.getBaseSleepTimeMs(),
                "retryPolicy.reconnectBaseSleepTime");
        assertEquals(configuration.getReconnectMaxRetries(), retryPolicy.getN(), "retryPolicy.reconnectMaxRetries");
        // retryPolicy.maxSleepMs not visible here

    }

}
