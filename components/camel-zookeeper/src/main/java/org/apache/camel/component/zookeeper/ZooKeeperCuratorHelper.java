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

import java.util.Optional;

import org.apache.camel.util.ObjectHelper;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder;
import org.apache.curator.x.discovery.details.JsonInstanceSerializer;

public final class ZooKeeperCuratorHelper {
    private ZooKeeperCuratorHelper() {
    }

    public static CuratorFramework createCurator(ZooKeeperCuratorConfiguration configuration) throws Exception {
        CuratorFramework curator = configuration.getCuratorFramework();
        if (curator == null) {
            // Validate parameters
            ObjectHelper.notNull(configuration.getNodes(), "ZooKeeper Nodes");

            RetryPolicy retryPolicy = configuration.getRetryPolicy();
            if (retryPolicy == null) {
                retryPolicy = new ExponentialBackoffRetry(
                    (int)configuration.getReconnectBaseSleepTimeUnit().toMillis(configuration.getReconnectBaseSleepTime()),
                    (int)configuration.getReconnectMaxSleepTimeUnit().toMillis(configuration.getReconnectMaxSleepTime()),
                    configuration.getReconnectMaxRetries());
            }

            CuratorFrameworkFactory.Builder builder = CuratorFrameworkFactory.builder()
                .connectString(String.join(",", configuration.getNodes()))
                .sessionTimeoutMs((int) configuration.getSessionTimeoutUnit().toMillis(configuration.getSessionTimeout()))
                .connectionTimeoutMs((int) configuration.getConnectionTimeoutUnit().toMillis(configuration.getConnectionTimeout()))
                .maxCloseWaitMs((int) configuration.getMaxCloseWaitUnit().toMillis(configuration.getMaxCloseWait()))
                .retryPolicy(retryPolicy);

            Optional.ofNullable(configuration.getNamespace()).ifPresent(builder::namespace);
            Optional.ofNullable(configuration.getAuthInfoList()).ifPresent(builder::authorization);

            curator = builder.build();
        }

        return curator;
    }

    public static <T> ServiceDiscovery<T> createServiceDiscovery(ZooKeeperCuratorConfiguration configuration, CuratorFramework curator, Class<T> payloadType) {
        return ServiceDiscoveryBuilder.builder(payloadType)
            .client(curator)
            .basePath(configuration.getBasePath())
            .serializer(new JsonInstanceSerializer<>(payloadType))
            .build();
    }
}
