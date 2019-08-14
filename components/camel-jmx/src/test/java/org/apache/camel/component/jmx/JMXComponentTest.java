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
package org.apache.camel.component.jmx;

import java.util.Map;

import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test behavior in the component for initializing an endpoint. Not much here beyond
 * checking that the code for the required and mutually exclusive params is working.
 */
public class JMXComponentTest {

    DefaultCamelContext context = new DefaultCamelContext();

    @Test
    public void withObjectProperties() throws Exception {
        JMXEndpoint ep = context.getEndpoint("jmx:platform?objectDomain=FooDomain&key.propOne=prop1&key.propTwo=prop2", JMXEndpoint.class);
        assertNotNull(ep);

        Map<String, String> props = ep.getObjectProperties();
        assertEquals(2, props.size());
        assertEquals("prop1", props.get("propOne"));
        assertEquals("prop2", props.get("propTwo"));

        assertNull(ep.getObjectName());
    }

    @Test
    public void withObjectName() throws Exception {
        JMXEndpoint ep = context.getEndpoint("jmx:platform?objectDomain=FooDomain&objectName=theObjectName", JMXEndpoint.class);
        assertNotNull(ep);

        assertEquals("theObjectName", ep.getObjectName());

        Map<String, String> props = ep.getObjectProperties();
        assertNull(props);
    }

    @Test
    public void withObjectNameAndObjectProperties() throws Exception {
        try {
            context.getEndpoint("jmx:platform?objectDomain=FooDomain&objectName=theObjectName&key.propOne=prop1");
            fail("expected exception");
        } catch (ResolveEndpointFailedException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }

    @Test
    public void withoutDomain() throws Exception {
        try {
            context.getEndpoint("jmx:platform?objectName=theObjectName");
            fail("missing domain should have caused failure");
        } catch (ResolveEndpointFailedException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }

    @Test
    public void withoutObjectNameAndObjectProperties() throws Exception {
        try {
            context.getEndpoint("jmx:platform?objectDomain=theObjectDomain");
            fail("missing name should have caused failure");
        } catch (ResolveEndpointFailedException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }
}
