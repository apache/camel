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
package org.apache.camel.component.infinispan;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.infinispan.processor.query.HavingQueryBuilderStrategy;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.infinispan.commons.api.BasicCache;
import org.infinispan.commons.api.BasicCacheContainer;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.junit.Before;
import org.junit.Test;
import static org.hamcrest.core.Is.is;

public class InfinispanEmbeddedQueryTest extends CamelTestSupport {
    protected BasicCacheContainer basicCacheContainer;
    protected HavingQueryBuilderStrategy queryBuilderStrategy =
            new HavingQueryBuilderStrategy(Book.class, "title", "Camel");

    @Override
    @Before
    public void setUp() throws Exception {
        Configuration infinispanConfiguration = new ConfigurationBuilder()
                .indexing()
                .enable()
                .indexLocalOnly(true)
                .addProperty("default.directory_provider", "ram")
                .build();

        basicCacheContainer = new DefaultCacheManager(infinispanConfiguration);
        basicCacheContainer.start();
        super.setUp();
    }

    @Override
    public void tearDown() throws Exception {
        basicCacheContainer.stop();
        super.tearDown();
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("cacheContainer", basicCacheContainer);
        registry.bind("queryBuilderStrategy", queryBuilderStrategy);
        return registry;
    }

    protected BasicCache<Object, Object> currentCache() {
        return basicCacheContainer.getCache();
    }

    @Test
    public void findsCacheEntryBasedOnTheValue() throws Exception {
        Book camelBook = new Book("1", "Camel", "123");
        Book activeMQBook = new Book("2", "ActiveMQ", "124");

        currentCache().put(camelBook.getId(), camelBook);
        currentCache().put(activeMQBook.getId(), activeMQBook);

        Exchange exchange = template.send("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.OPERATION, InfinispanConstants.QUERY);
            }
        });

        List<Book> result = exchange.getIn().getHeader(InfinispanConstants.RESULT, List.class);
        assertThat(result.size(), is(1));
        assertThat(result.get(0), is(camelBook));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("infinispan://localhost?cacheContainer=#cacheContainer&queryBuilderStrategy=#queryBuilderStrategy");
            }
        };
    }
}
