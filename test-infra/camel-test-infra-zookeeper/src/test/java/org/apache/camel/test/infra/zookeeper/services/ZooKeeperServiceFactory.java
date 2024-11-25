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
package org.apache.camel.test.infra.zookeeper.services;

import org.apache.camel.test.infra.common.services.ContainerTestService;
import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

public final class ZooKeeperServiceFactory {
    private static class SingletonZooKeeperService extends SingletonService<ZooKeeperTestService>
            implements ZooKeeperTestService {
        public SingletonZooKeeperService(ZooKeeperTestService service, String name) {
            super(service, name);
        }

        @Override
        public String getConnectionString() {
            return getService().getConnectionString();
        }
    }

    private ZooKeeperServiceFactory() {

    }

    public static SimpleTestServiceBuilder<ZooKeeperTestService> builder() {
        return new SimpleTestServiceBuilder<>("zookeeper");
    }

    public static ZooKeeperTestService createService() {
        return builder()
                .addLocalMapping(ZooKeeperLocalContainerTestService::new)
                .addRemoteMapping(ZooKeeperRemoteTestService::new)
                .build();
    }
}

class ZooKeeperLocalContainerTestService extends ZooKeeperLocalContainerService
        implements ZooKeeperTestService, ContainerTestService {
}

class ZooKeeperRemoteTestService extends ZooKeeperRemoteService implements ZooKeeperTestService {
}
