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
package org.apache.camel.test.infra.mcp.everything.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

public final class McpEverythingServiceFactory {

    private static class SingletonMcpEverythingService extends SingletonService<McpEverythingService>
            implements McpEverythingService {
        public SingletonMcpEverythingService(McpEverythingService service, String name) {
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

    private McpEverythingServiceFactory() {
    }

    public static SimpleTestServiceBuilder<McpEverythingService> builder() {
        return new SimpleTestServiceBuilder<>("mcp-everything");
    }

    public static McpEverythingService createService() {
        return builder()
                .addLocalMapping(McpEverythingLocalContainerService::new)
                .build();
    }

    public static McpEverythingService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final McpEverythingService INSTANCE;
        static {
            SimpleTestServiceBuilder<McpEverythingService> instance = builder();
            instance.addLocalMapping(
                    () -> new SingletonMcpEverythingService(new McpEverythingLocalContainerService(), "mcp-everything"));
            INSTANCE = instance.build();
        }
    }

    public static class McpEverythingLocalContainerService extends McpEverythingLocalContainerInfraService
            implements McpEverythingService {
    }
}
