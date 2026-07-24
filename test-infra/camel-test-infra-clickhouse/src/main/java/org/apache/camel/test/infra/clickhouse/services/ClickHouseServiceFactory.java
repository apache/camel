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
package org.apache.camel.test.infra.clickhouse.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;

public final class ClickHouseServiceFactory {
    private ClickHouseServiceFactory() {

    }

    public static SimpleTestServiceBuilder<ClickHouseService> builder() {
        return new SimpleTestServiceBuilder<>("clickhouse");
    }

    public static ClickHouseService createService() {
        return builder()
                .addLocalMapping(ClickHouseLocalContainerService::new)
                .addRemoteMapping(RemoteClickHouseService::new)
                .build();
    }

    public static ClickHouseService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    public static class RemoteClickHouseService extends RemoteClickHouseInfraService implements ClickHouseService {
    }

    private static class SingletonServiceHolder {
        static final ClickHouseService INSTANCE;
        static {
            SimpleTestServiceBuilder<ClickHouseService> instance = builder();
            instance.addLocalMapping(ClickHouseLocalContainerService::new)
                    .addRemoteMapping(RemoteClickHouseService::new);
            INSTANCE = instance.build();
        }
    }
}
