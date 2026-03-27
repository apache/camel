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
package org.apache.camel.test.infra.redis.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

public final class RedisServiceFactory {

    private static class SingletonRedisService extends SingletonService<RedisService> implements RedisService {
        public SingletonRedisService(RedisService service, String name) {
            super(service, name);
        }

        @Override
        public String host() {
            return getService().host();
        }

        @Override
        public int port() {
            return getService().port();
        }
    }

    private RedisServiceFactory() {

    }

    public static SimpleTestServiceBuilder<RedisService> builder() {
        return new SimpleTestServiceBuilder<>("redis");
    }

    public static RedisService createService() {
        return builder()
                .addLocalMapping(RedisLocalContainerService::new)
                .addRemoteMapping(RedisRemoteService::new)
                .build();
    }

    public static RedisService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final RedisService INSTANCE;
        static {
            SimpleTestServiceBuilder<RedisService> instance = builder();
            instance.addLocalMapping(
                    () -> new SingletonRedisService(new RedisLocalContainerService(), "redis"))
                    .addRemoteMapping(RedisRemoteService::new);
            INSTANCE = instance.build();
        }
    }

    public static class RedisRemoteService extends RedisRemoteInfraService implements RedisService {
    }

    public static class RedisLocalContainerService extends RedisLocalContainerInfraService implements RedisService {
    }
}
