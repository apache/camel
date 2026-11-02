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
package org.apache.camel.component.servicenow;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.camel.CamelContext;
import org.apache.camel.component.servicenow.model.Incident;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Tests how a ServiceNow "result" node is unwrapped into the message body for the different model types, without
 * calling a ServiceNow instance.
 */
public class ServiceNowUnwrapTest {

    private static final String ARRAY = "[{\"sys_id\":\"1\",\"number\":\"INC1\"},{\"sys_id\":\"2\",\"number\":\"INC2\"}]";

    private CamelContext context;
    private AbstractServiceNowProcessor processor;

    @BeforeEach
    public void setUp() {
        context = new DefaultCamelContext();
        context.start();

        ServiceNowEndpoint endpoint
                = context.getEndpoint("servicenow:myinstance?userName=user&password=pwd", ServiceNowEndpoint.class);
        processor = new AbstractServiceNowProcessor(endpoint) {
        };
    }

    @AfterEach
    public void tearDown() {
        context.stop();
    }

    @Test
    public void testArrayWithMapModel() throws Exception {
        Object result = processor.unwrap(readTree(ARRAY), Map.class);

        List<?> list = assertInstanceOf(List.class, result);
        assertEquals(2, list.size());
        Map<?, ?> first = assertInstanceOf(Map.class, list.get(0));
        assertEquals("INC1", first.get("number"));
        Map<?, ?> second = assertInstanceOf(Map.class, list.get(1));
        assertEquals("INC2", second.get("number"));
    }

    @Test
    public void testArrayWithMapSubclassModel() throws Exception {
        Object result = processor.unwrap(readTree(ARRAY), IncidentMap.class);

        List<?> list = assertInstanceOf(List.class, result);
        assertEquals(2, list.size());
        IncidentMap first = assertInstanceOf(IncidentMap.class, list.get(0));
        assertEquals("INC1", first.get("number"));
        IncidentMap second = assertInstanceOf(IncidentMap.class, list.get(1));
        assertEquals("INC2", second.get("number"));
    }

    @Test
    public void testArrayWithPojoModel() throws Exception {
        Object result = processor.unwrap(readTree(ARRAY), Incident.class);

        List<?> list = assertInstanceOf(List.class, result);
        assertEquals(2, list.size());
        Incident first = assertInstanceOf(Incident.class, list.get(0));
        assertEquals("1", first.getId());
        assertEquals("INC1", first.getNumber());
    }

    @Test
    public void testObjectWithMapModel() throws Exception {
        Object result = processor.unwrap(readTree("{\"sys_id\":\"1\",\"number\":\"INC1\"}"), Map.class);

        Map<?, ?> map = assertInstanceOf(Map.class, result);
        assertEquals("INC1", map.get("number"));
    }

    @Test
    public void testArrayWithStringModel() throws Exception {
        Object result = processor.unwrap(readTree(ARRAY), String.class);

        assertEquals(ARRAY, result);
    }

    private JsonNode readTree(String json) throws Exception {
        return processor.mapper.readTree(json);
    }

    public static class IncidentMap extends HashMap<String, Object> {
    }
}
