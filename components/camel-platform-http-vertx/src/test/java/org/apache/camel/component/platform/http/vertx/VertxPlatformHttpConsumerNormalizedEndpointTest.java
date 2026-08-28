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
package org.apache.camel.component.platform.http.vertx;

import org.apache.camel.Consumer;
import org.apache.camel.spi.RestRegistry.RestService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test for {@link VertxPlatformHttpConsumer#buildNormalizedEndpoint(RestService)}, in isolation from the
 * OpenAPI/Vert.x wiring, covering the contract-first root basePath double-slash bug.
 */
public class VertxPlatformHttpConsumerNormalizedEndpointTest {

    @Test
    public void rootBasePathIsNotDoubled() {
        assertEquals("/pet/{petId}", VertxPlatformHttpConsumer.buildNormalizedEndpoint(restService("/", "/pet/{petId}")));
    }

    @Test
    public void nonRootBasePathIsUnchanged() {
        assertEquals("/api/v3/pet", VertxPlatformHttpConsumer.buildNormalizedEndpoint(restService("/api/v3", "/pet")));
    }

    @Test
    public void trailingSlashOnNonRootBasePathIsStripped() {
        assertEquals("/api/v3/pet", VertxPlatformHttpConsumer.buildNormalizedEndpoint(restService("/api/v3/", "/pet")));
    }

    private static RestService restService(String basePath, String baseUrl) {
        return new StubRestService(basePath, baseUrl);
    }

    private static final class StubRestService implements RestService {

        private final String basePath;
        private final String baseUrl;

        StubRestService(String basePath, String baseUrl) {
            this.basePath = basePath;
            this.baseUrl = baseUrl;
        }

        @Override
        public Consumer getConsumer() {
            return null;
        }

        @Override
        public boolean isSpecification() {
            return false;
        }

        @Override
        public boolean isContractFirst() {
            return true;
        }

        @Override
        public String getState() {
            return null;
        }

        @Override
        public String getRouteId() {
            return null;
        }

        @Override
        public String getOperationId() {
            return null;
        }

        @Override
        public String getUrl() {
            return null;
        }

        @Override
        public String getBaseUrl() {
            return baseUrl;
        }

        @Override
        public String getBasePath() {
            return basePath;
        }

        @Override
        public String getUriTemplate() {
            return null;
        }

        @Override
        public String getMethod() {
            return null;
        }

        @Override
        public String getConsumes() {
            return null;
        }

        @Override
        public String getProduces() {
            return null;
        }

        @Override
        public String getInType() {
            return null;
        }

        @Override
        public String getOutType() {
            return null;
        }

        @Override
        public String getDescription() {
            return null;
        }

        @Override
        public String getSpecificationUri() {
            return null;
        }

        @Override
        public long getHits() {
            return 0;
        }
    }
}
