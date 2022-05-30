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
package org.apache.camel.component.infinispan;

import java.util.UUID;
import java.util.stream.IntStream;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.IdempotentRepository;
import org.infinispan.commons.api.BasicCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public interface InfinispanIdempotentRepositoryTestSupport {
    IdempotentRepository getIdempotentRepository();

    BasicCache<Object, Object> getCache();

    MockEndpoint getMockEndpoint(String id);

    ProducerTemplate template();

    @Test
    default void addsNewKeysToCache() {
        assertTrue(getIdempotentRepository().add("One"));
        assertTrue(getIdempotentRepository().add("Two"));

        assertTrue(getCache().containsKey("One"));
        assertTrue(getCache().containsKey("Two"));
    }

    @Test
    default void skipsAddingSecondTimeTheSameKey() {
        assertTrue(getIdempotentRepository().add("One"));
        assertFalse(getIdempotentRepository().add("One"));
    }

    @Test
    default void containsPreviouslyAddedKey() {
        assertFalse(getIdempotentRepository().contains("One"));

        getIdempotentRepository().add("One");

        assertTrue(getIdempotentRepository().contains("One"));
    }

    @Test
    default void removesAnExistingKey() {
        getIdempotentRepository().add("One");

        assertTrue(getIdempotentRepository().remove("One"));

        assertFalse(getIdempotentRepository().contains("One"));
    }

    @Test
    default void doesntRemoveMissingKey() {
        assertFalse(getIdempotentRepository().remove("One"));
    }

    @Test
    default void clearCache() {
        assertTrue(getIdempotentRepository().add("One"));
        assertTrue(getIdempotentRepository().add("Two"));

        assertTrue(getCache().containsKey("One"));
        assertTrue(getCache().containsKey("Two"));

        getIdempotentRepository().clear();

        assertFalse(getCache().containsKey("One"));
        assertFalse(getCache().containsKey("Two"));
    }

    @Test
    default void producerQueryOperationWithoutQueryBuilder() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        final String messageId = UUID.randomUUID().toString();
        IntStream.range(0, 10).forEach(
                i -> template().sendBodyAndHeader("direct:start", "message-" + i, "MessageID", messageId));

        mock.assertIsSatisfied();
    }
}
