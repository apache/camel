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
package org.apache.camel.component.infinispan.embedded;

import java.util.function.Supplier;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.infinispan.InfinispanIdempotentRepositoryTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.util.function.Suppliers;
import org.infinispan.commons.api.BasicCache;
import org.junit.jupiter.api.BeforeEach;

public class InfinispanEmbeddedIdempotentRepositoryTest extends InfinispanEmbeddedTestSupport
        implements InfinispanIdempotentRepositoryTestSupport {

    private Supplier<IdempotentRepository> repo = Suppliers.memorize(() -> {
        InfinispanEmbeddedIdempotentRepository repo = new InfinispanEmbeddedIdempotentRepository(getCacheName());
        repo.setCacheContainer(cacheContainer);

        return repo;
    });

    @BeforeEach
    protected void beforeEach() {
        // cleanup the default test cache before each run
        getCache().clear();
    }

    @Override
    public IdempotentRepository getIdempotentRepository() {
        return repo.get();
    }

    @Override
    public BasicCache<Object, Object> getCache() {
        return super.getCache();
    }

    @Override
    public MockEndpoint getMockEndpoint(String id) {
        return super.getMockEndpoint(id);
    }

    @Override
    public BasicCache<Object, Object> getCache(String name) {
        return super.getCache(name);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .idempotentConsumer(
                                header("MessageID"),
                                getIdempotentRepository())
                        .skipDuplicate(true)
                        .to("mock:result");
            }
        };
    }
}
