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
package org.apache.camel.processor.idempotent;

import java.util.List;

import static java.util.stream.Collectors.toList;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.IdempotentRepository;
import org.junit.Ignore;
import org.junit.Test;

import static org.apache.camel.processor.idempotent.MemoryIdempotentRepository.memoryIdempotentRepository;

public class MemoryIdempotentRepositoryTest extends AbstractIdempotentRepositoryTest<IdempotentRepository<String>> {

    public MemoryIdempotentRepositoryTest() {
        super(createRepo());
    }

    private static IdempotentRepository<String> createRepo() {
        return memoryIdempotentRepository(20);
    }

    @Test
    @Ignore("lru test seems non-deterministic")
    public void usesLRUSemantics() throws Exception {
        // Given:
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .idempotentConsumer(body(), repo)
                        .to("mock:result");
            }
        });
        startCamelContext();
        getMockEndpoint("mock:result").expectedBodiesReceived(allOf("abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz"));
        // When:
        allOf("abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz").forEach(c -> sendBodies("direct:start", c));
        // Then:
        assertMockEndpointsSatisfied();
    }

    private static List<Character> allOf(String letters) {
        return letters.chars().mapToObj(i -> (char)i).collect(toList());
    }
}