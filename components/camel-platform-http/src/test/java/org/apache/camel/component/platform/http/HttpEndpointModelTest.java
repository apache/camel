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
package org.apache.camel.component.platform.http;

import java.util.HashSet;
import java.util.Set;

import org.apache.camel.Consumer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class HttpEndpointModelTest {

    @Test
    void setRetainsMultipleConsumersOnSamePath() {
        Consumer getConsumer = mock(Consumer.class);
        Consumer postConsumer = mock(Consumer.class);

        HttpEndpointModel getModel = new HttpEndpointModel("/shared", "GET", null, null, getConsumer);
        HttpEndpointModel postModel = new HttpEndpointModel("/shared", "POST", null, null, postConsumer);

        Set<HttpEndpointModel> endpoints = new HashSet<>();
        assertTrue(endpoints.add(getModel));
        assertTrue(endpoints.add(postModel));
        assertEquals(2, endpoints.size());
    }

    @Test
    void equalsAndHashCodeUseConsumerIdentity() {
        Consumer first = mock(Consumer.class);
        Consumer second = mock(Consumer.class);

        HttpEndpointModel firstModel = new HttpEndpointModel("/shared", "GET", null, null, first);
        HttpEndpointModel secondModel = new HttpEndpointModel("/shared", "POST", null, null, second);
        HttpEndpointModel sameConsumerModel = new HttpEndpointModel("/shared", "GET", null, null, first);

        assertNotEquals(firstModel, secondModel);
        assertEquals(firstModel, sameConsumerModel);
        assertEquals(firstModel.hashCode(), sameConsumerModel.hashCode());
    }

    @Test
    void compareToIsConsistentWithEquals() {
        Consumer first = mock(Consumer.class);
        Consumer second = mock(Consumer.class);

        HttpEndpointModel firstModel = new HttpEndpointModel("/shared", "GET", null, null, first);
        HttpEndpointModel secondModel = new HttpEndpointModel("/shared", "POST", null, null, second);
        HttpEndpointModel sameConsumerModel = new HttpEndpointModel("/shared", "GET", null, null, first);

        assertEquals(0, firstModel.compareTo(sameConsumerModel));
        assertNotEquals(0, firstModel.compareTo(secondModel));
    }
}
