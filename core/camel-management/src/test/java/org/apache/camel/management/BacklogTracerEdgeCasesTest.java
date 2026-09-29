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

import java.io.StringReader;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.xml.sax.InputSource;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.BacklogTracer;
import org.apache.camel.spi.BacklogTracerEventMessage;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class BacklogTracerEdgeCasesTest extends ManagementTestSupport {

    private BacklogTracer tracer;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        tracer = context.getCamelContextExtension().getContextPlugin(BacklogTracer.class);
        tracer.setEnabled(true);
    }

    @Test
    public void testTraceFilterWithColon() throws Exception {
        tracer.setTraceFilter("${header.foo} == 'a:b'");

        template.sendBodyAndHeader("direct:start", "Hello", "foo", "a:b");
        template.sendBodyAndHeader("direct:start", "Bye", "foo", "c");

        List<BacklogTracerEventMessage> events = tracer.dumpAllTracedMessages();
        assertFalse(events.isEmpty());
        assertTrue(events.stream().allMatch(e -> e.getMessageAsJSon().contains("Hello")));
    }

    @Test
    public void testTraceFilterWithLanguagePrefix() throws Exception {
        tracer.setTraceFilter("simple:${header.foo} == 'x'");

        template.sendBodyAndHeader("direct:start", "Hello", "foo", "x");
        template.sendBodyAndHeader("direct:start", "Bye", "foo", "y");

        List<BacklogTracerEventMessage> events = tracer.dumpAllTracedMessages();
        assertFalse(events.isEmpty());
        assertTrue(events.stream().allMatch(e -> e.getMessageAsJSon().contains("Hello")));
    }

    @Test
    public void testTraceFilterThatFailsDoesNotFailTheExchange() throws Exception {
        tracer.setTraceFilter("${body.noSuchMethod()}");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        template.sendBody("direct:start", "Hello");
        mock.assertIsSatisfied();
        assertNull(mock.getReceivedExchanges().get(0).getException());
    }

    @Test
    public void testNodeLabelInJSonIsNotEscapedTwice() throws Exception {
        template.sendBody("direct:start", "Hello");

        BacklogTracerEventMessage event = tracer.dumpAllTracedMessages().stream()
                .filter(e -> e.getToNodeLabel() != null && e.getToNodeLabel().contains("mock:a/b"))
                .findFirst().orElseThrow();
        JsonObject jo = (JsonObject) Jsoner.deserialize(event.toJSon(0));
        assertEquals(event.getToNodeLabel(), jo.getString("nodeLabel"));
    }

    @Test
    public void testDumpAsXmlIsWellFormed() throws Exception {
        tracer.setRemoveOnDump(false);
        template.sendBody("direct:start", "Hello");
        template.sendBody("direct:amp", "Hello");

        String xml = tracer.dumpAllTracedMessagesAsXml();
        assertNotNull(xml);
        assertTrue(xml.contains("<routeId>a&amp;b</routeId>"), xml);
        // endpoint uris and route ids with & must be encoded
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.setUseBreadcrumb(true);
                context.setBacklogTracing(true);
                context.setMessageHistory(true);

                from("direct:start").routeId("start")
                        .to("log:foo")
                        .to("mock:a/b")
                        .to("mock:result?a=1&b=2");

                from("direct:amp").routeId("a&b")
                        .to("mock:amp");
            }
        };
    }
}
