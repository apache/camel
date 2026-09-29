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
package org.apache.camel.processor.aggregate.zipfile;

import java.io.File;
import java.io.FileInputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.MemoryAggregationRepository;
import org.apache.camel.spi.AggregationRepository;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit6.TestSupport.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The zip file that the strategy appends to must not be deleted before the group completes, when the aggregation
 * repository does not keep the exchange instances (optimistic locking, or a persistent repository).
 */
public class ZipAggregationStrategyRepositoryTest extends CamelTestSupport {

    private static final String TEST_DIR = "target/out_ZipAggregationStrategyRepositoryTest";

    @BeforeEach
    public void deleteTestDirs() {
        deleteDirectory(TEST_DIR);
    }

    @Test
    public void testOptimisticLocking() throws Exception {
        sendAndAssertZip("optimistic");
    }

    @Test
    public void testRepositoryStoringCopies() throws Exception {
        sendAndAssertZip("copies");
    }

    private void sendAndAssertZip(String name) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:" + name);
        mock.expectedMessageCount(1);

        template.sendBody("direct:" + name, "Hello");
        template.sendBody("direct:" + name, "Hello again");
        template.sendBody("direct:" + name, "Bye");

        MockEndpoint.assertIsSatisfied(context);

        File[] files = new File(TEST_DIR, name).listFiles();
        assertNotNull(files);
        assertEquals(1, files.length);
        int count = 0;
        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(files[0]))) {
            for (ZipEntry ze = zin.getNextEntry(); ze != null; ze = zin.getNextEntry()) {
                count++;
            }
        }
        assertEquals(3, count, "Zip file should contain 3 files");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:optimistic")
                        .aggregate(constant(true), strategy())
                        .aggregationRepository(new MemoryAggregationRepository(true)).optimisticLocking()
                        .completionSize(3)
                        .to("file:" + TEST_DIR + "/optimistic")
                        .to("mock:optimistic");

                from("direct:copies")
                        .aggregate(constant(true), strategy())
                        .aggregationRepository(new CopyingRepository())
                        .completionSize(3)
                        .to("file:" + TEST_DIR + "/copies")
                        .to("mock:copies");
            }
        };
    }

    private static ZipAggregationStrategy strategy() {
        ZipAggregationStrategy strategy = new ZipAggregationStrategy();
        strategy.setParentDir(TEST_DIR + "/temp");
        return strategy;
    }

    /**
     * A repository that stores a copy of the exchange, as the persistent repositories do.
     */
    private static final class CopyingRepository extends ServiceSupport implements AggregationRepository {
        private final Map<String, Exchange> exchanges = new ConcurrentHashMap<>();

        @Override
        public Exchange add(CamelContext camelContext, String key, Exchange exchange) {
            return exchanges.put(key, exchange.copy());
        }

        @Override
        public Exchange get(CamelContext camelContext, String key) {
            Exchange exchange = exchanges.get(key);
            return exchange != null ? exchange.copy() : null;
        }

        @Override
        public void remove(CamelContext camelContext, String key, Exchange exchange) {
            exchanges.remove(key);
        }

        @Override
        public void confirm(CamelContext camelContext, String exchangeId) {
            // noop
        }

        @Override
        public Set<String> getKeys() {
            return exchanges.keySet();
        }
    }
}
