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
package org.apache.camel.impl.cloud;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.cloud.ServiceDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ServiceDefinitionTest {
    @Test
    public void testParse() {
        List<? extends ServiceDefinition> definitions = DefaultServiceDefinition.parse(
                "svc1@host:2001,myId/svc1@host:2001").toList();

        assertEquals(2, definitions.size());

        assertNull(definitions.get(0).getId());
        assertEquals("svc1", definitions.get(0).getName());
        assertEquals("host", definitions.get(0).getHost());
        assertEquals(2001, definitions.get(0).getPort());

        assertEquals("myId", definitions.get(1).getId());
        assertEquals("svc1", definitions.get(1).getName());
        assertEquals("host", definitions.get(1).getHost());
        assertEquals(2001, definitions.get(1).getPort());
    }

    @Test
    public void testMatch() {
        List<ServiceDefinition> definitions = new ArrayList<>();
        definitions.add(
                DefaultServiceDefinition.builder().withName("service-1").withHost("host-1.domain1.com").withPort(2001).build());
        definitions.add(
                DefaultServiceDefinition.builder().withName("service-2").withHost("host-2.domain1.com").withPort(2001).build());
        definitions.add(
                DefaultServiceDefinition.builder().withName("service-3").withHost("host-3.domain1.com").withPort(2001).build());
        definitions.add(
                DefaultServiceDefinition.builder().withName("service-4").withHost("host-3.domain2.com").withPort(2001).build());

        assertTrue(
                DefaultServiceDefinition.builder().withName("*").withHost(".*\\.domain1\\.com").withPort(2001).build().matches(
                        definitions.get(0)));
        assertTrue(
                DefaultServiceDefinition.builder().withName("service-1").withHost("host-1.domain1.com").withPort(2001).build()
                        .matches(
                                definitions.get(0)));
        assertTrue(
                DefaultServiceDefinition.builder().withName("service-.*").withHost("host-1.domain1.com").withPort(2001).build()
                        .matches(
                                definitions.get(0)));
        assertTrue(
                DefaultServiceDefinition.builder().withName("service-.*").withHost("host-.*\\.domain.*\\.com").withPort(2001)
                        .build().matches(
                                definitions.get(0)));
        assertTrue(
                DefaultServiceDefinition.builder().withName("service-.*").withHost("host-.*\\.domain.*\\.com").withPort(2001)
                        .build().matches(
                                definitions.get(1)));
        assertTrue(
                DefaultServiceDefinition.builder().withName("service-.*").withHost("host-.*\\.domain.*\\.com").withPort(2001)
                        .build().matches(
                                definitions.get(2)));
        assertTrue(
                DefaultServiceDefinition.builder().withName("service-.*").withHost("host-.*\\.domain.*\\.com").withPort(2001)
                        .build().matches(
                                definitions.get(3)));
        assertFalse(
                DefaultServiceDefinition.builder().withName("service-.*").withHost("host-1.domain1.com").withPort(2001).build()
                        .matches(
                                definitions.get(3)));
        assertFalse(
                DefaultServiceDefinition.builder().withName("*").withHost(".*\\.domain1\\.com").withPort(2001).build().matches(
                        definitions.get(3)));
        assertFalse(
                DefaultServiceDefinition.builder().withName("*").withHost(".*\\.domain1\\.com").withPort(2002).build().matches(
                        definitions.get(3)));
    }
}
