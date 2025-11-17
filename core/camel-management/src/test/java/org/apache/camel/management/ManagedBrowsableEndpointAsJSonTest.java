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
package org.apache.camel.management;

import java.util.HashMap;
import java.util.Map;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_ENDPOINT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@DisabledOnOs(OS.AIX)
public class ManagedBrowsableEndpointAsJSonTest extends ManagementTestSupport {

    @Test
    public void testBrowseableEndpointAsJSonIncludeBody() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        Map<String, Object> headers = new HashMap<>();
        headers.put("user", false);
        headers.put("uid", 123);
        headers.put("title", "Camel rocks");
        template.sendBodyAndHeaders("direct:start", "Hello World", headers);

        assertMockEndpointsSatisfied();

        MBeanServer mbeanServer = getMBeanServer();

        ObjectName name = getCamelObjectName(TYPE_ENDPOINT, "mock://result");

        String out = (String) mbeanServer.invoke(name, "browseMessageAsJSon", new Object[] { 0, true },
                new String[] { "java.lang.Integer", "java.lang.Boolean" });
        assertNotNull(out);
        log.info(out);
        assertTrue(out.contains("\"body\": {"));
        assertTrue(out.contains("\"value\": \"Hello World\""));
    }

    @Test
    public void testBrowseableEndpointAsJSon() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(2);

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", 123);
        template.sendBodyAndHeader("direct:start", "Bye World", "foo", 456);

        assertMockEndpointsSatisfied();

        MBeanServer mbeanServer = getMBeanServer();

        ObjectName name = getCamelObjectName(TYPE_ENDPOINT, "mock://result");

        String out = (String) mbeanServer.invoke(name, "browseMessageAsJSon", new Object[] { 0, false },
                new String[] { "java.lang.Integer", "java.lang.Boolean" });
        assertNotNull(out);
        assertFalse(out.contains("\"body\": {"));
        assertTrue(out.contains("\"value\": 123"));

        out = (String) mbeanServer.invoke(name, "browseMessageAsJSon", new Object[] { 1, false },
                new String[] { "java.lang.Integer", "java.lang.Boolean" });
        assertNotNull(out);
        assertFalse(out.contains("\"body\": {"));
        assertTrue(out.contains("\"value\": 456"));
    }

    @Test
    public void testBrowseableEndpointAsJSonAllIncludeBody() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(2);

        template.sendBody("direct:start", "Hello World");
        template.sendBodyAndHeader("direct:start", "Bye World", "foo", 456);

        assertMockEndpointsSatisfied();

        MBeanServer mbeanServer = getMBeanServer();

        ObjectName name = getCamelObjectName(TYPE_ENDPOINT, "mock://result");

        String out = (String) mbeanServer.invoke(name, "browseAllMessagesAsJSon", new Object[] { true },
                new String[] { "java.lang.Boolean" });
        assertNotNull(out);
        log.info(out);

        assertTrue(out.contains("\"value\": \"Hello World\""));
        assertTrue(out.contains("\"value\": \"Bye World\""));
    }

    @Test
    public void testBrowseableEndpointAsJSonAll() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(2);

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", 123);
        template.sendBodyAndHeader("direct:start", "Bye World", "foo", 456);

        assertMockEndpointsSatisfied();

        MBeanServer mbeanServer = getMBeanServer();

        ObjectName name = getCamelObjectName(TYPE_ENDPOINT, "mock://result");

        String out = (String) mbeanServer.invoke(name, "browseAllMessagesAsJSon", new Object[] { false },
                new String[] { "java.lang.Boolean" });
        assertNotNull(out);
        log.info(out);

        assertFalse(out.contains("\"body\": {"));
        assertTrue(out.contains("\"value\": 123"));
        assertTrue(out.contains("\"value\": 456"));
    }

    @Test
    public void testBrowseableEndpointAsJSonRangeIncludeBody() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(3);

        template.sendBody("direct:start", "Hello World");
        template.sendBodyAndHeader("direct:start", "Bye World", "foo", 456);
        template.sendBody("direct:start", "Hi Camel");

        assertMockEndpointsSatisfied();

        MBeanServer mbeanServer = getMBeanServer();

        ObjectName name = getCamelObjectName(TYPE_ENDPOINT, "mock://result");

        String out = (String) mbeanServer.invoke(name, "browseRangeMessagesAsJSon", new Object[] { 0, 1, true },
                new String[] { "java.lang.Integer", "java.lang.Integer", "java.lang.Boolean" });
        assertNotNull(out);
        log.info(out);

        assertTrue(out.contains("\"value\": \"Hello World\""));
        assertTrue(out.contains("\"value\": \"Bye World\""));
    }

    @Test
    public void testBrowseableEndpointAsJSonRange() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(3);

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", 123);
        template.sendBodyAndHeader("direct:start", "Bye World", "foo", 456);
        template.sendBody("direct:start", "Hi Camel");

        assertMockEndpointsSatisfied();

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName name = getCamelObjectName(TYPE_ENDPOINT, "mock://result");

        String out = (String) mbeanServer.invoke(name, "browseRangeMessagesAsJSon", new Object[] { 0, 1, false },
                new String[] { "java.lang.Integer", "java.lang.Integer", "java.lang.Boolean" });
        assertNotNull(out);
        log.info(out);

        assertFalse(out.contains("\"body\": {"));
        assertTrue(out.contains("\"value\": 123"));
        assertTrue(out.contains("\"value\": 456"));
    }

    @Test
    public void testBrowseableEndpointAsJSonRangeInvalidIndex() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();

        ObjectName name = getCamelObjectName(TYPE_ENDPOINT, "mock://result");

        try {
            mbeanServer.invoke(name, "browseRangeMessagesAsJSon", new Object[] { 3, 1, false },
                    new String[] { "java.lang.Integer", "java.lang.Integer", "java.lang.Boolean" });
            fail("Should have thrown exception");
        } catch (Exception e) {
            assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("From index cannot be larger than to index, was: 3 > 1", e.getCause().getMessage());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.setUseBreadcrumb(false);

                from("direct:start").to("mock:result");
            }
        };
    }

}
