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
package org.apache.camel.component.hibernate;

import java.util.Map;

import org.apache.camel.component.hibernate.entity.HibernateTestEntity;
import org.apache.camel.impl.DefaultCamelContext;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HibernateEndpointTest {

    @Test
    void shouldStartWithSelectionQuery() throws Exception {
        HibernateEndpoint endpoint = createEndpoint();
        endpoint.setSelectionQuery("from HibernateTestEntity");

        assertDoesNotThrow(endpoint::start);

        endpoint.stop();
    }

    @Test
    void shouldStartWithMutationQuery() throws Exception {
        HibernateEndpoint endpoint = createEndpoint();
        endpoint.setMutationQuery("update HibernateTestEntity set name = :name");

        assertDoesNotThrow(endpoint::start);

        endpoint.stop();
    }

    @Test
    void shouldStartWithNaturalIdParameters() throws Exception {
        HibernateEndpoint endpoint = createEndpoint();
        endpoint.setNaturalIdParameters(Map.of("name", "test"));

        assertDoesNotThrow(endpoint::start);

        endpoint.stop();
    }

    @Test
    void shouldStartWithStatelessInsert() throws Exception {
        HibernateEndpoint endpoint = createEndpoint();
        endpoint.setStatelessOperation(HibernateEndpoint.StatelessOperation.INSERT);

        assertDoesNotThrow(endpoint::start);

        endpoint.stop();
    }

    @Test
    void shouldStartWithStatelessUpsert() throws Exception {
        HibernateEndpoint endpoint = createEndpoint();
        endpoint.setStatelessOperation(HibernateEndpoint.StatelessOperation.UPSERT);

        assertDoesNotThrow(endpoint::start);

        endpoint.stop();
    }

    @Test
    void shouldRejectMultipleOperations() {
        HibernateEndpoint endpoint = createEndpoint();
        endpoint.setSelectionQuery("from HibernateTestEntity");
        endpoint.setMutationQuery("update HibernateTestEntity set name = :name");

        assertThrows(IllegalArgumentException.class, endpoint::start);
    }

    @Test
    void shouldRejectNoOperation() {
        HibernateEndpoint endpoint = createEndpoint();

        assertThrows(IllegalArgumentException.class, endpoint::start);
    }

    @Test
    void shouldRejectStreamingWithoutSelectionQuery() {
        HibernateEndpoint endpoint = createEndpoint();
        endpoint.setStreaming(true);
        endpoint.setMutationQuery("update HibernateTestEntity set name = :name");

        assertThrows(IllegalArgumentException.class, endpoint::start);
    }

    @Test
    void shouldRejectStreamingWithNaturalIdParameters() {
        HibernateEndpoint endpoint = createEndpoint();
        endpoint.setStreaming(true);
        endpoint.setNaturalIdParameters(Map.of("name", "test"));

        assertThrows(IllegalArgumentException.class, endpoint::start);
    }

    private HibernateEndpoint createEndpoint() {
        HibernateEndpoint endpoint = new HibernateEndpoint();
        endpoint.setCamelContext(new DefaultCamelContext());
        endpoint.setSessionFactory(Mockito.mock(SessionFactory.class));
        endpoint.setEntityType(HibernateTestEntity.class);
        return endpoint;
    }
}
