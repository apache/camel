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
package org.apache.camel.commands;

import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Message;
import org.apache.camel.ValidationException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.ExplicitCamelContextNameStrategy;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.Validator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AbstractLocalCamelControllerTest {

    private final DummyCamelController localCamelController;

    private final CamelContext context;

    public AbstractLocalCamelControllerTest() throws Exception {
        context = new DefaultCamelContext();
        context.setNameStrategy(new ExplicitCamelContextNameStrategy("context1"));

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                transformer()
                    .fromType("xml:foo")
                    .toType("json:bar")
                    .withJava(DummyTransformer.class);
                validator()
                    .type("xml:foo")
                    .withJava(DummyValidator.class);
                from("direct:start1").id("route1").delay(100).to("mock:result1");
                from("direct:start2").id("route2").delay(100).to("mock:result2");
                from("direct:start3").id("route3").delay(100).to("mock:result3");
            }
        });
        localCamelController = new DummyCamelController(context);
    }

    @Before
    public void startContext() throws Exception {
        context.start();
    }

    @After
    public void stopContext() throws Exception {
        context.stop();
    }

    @Test
    public void testBrowseInflightExchangesWithMoreRoutes() throws Exception {
        context.createProducerTemplate().asyncSendBody("direct:start1", "Start one");
        context.createProducerTemplate().asyncSendBody("direct:start2", "Start two");
        context.createProducerTemplate().asyncSendBody("direct:start3", "Start three");

        // let the exchange proceed
        Thread.sleep(50);

        final List<Map<String, Object>> inflightExchanges = localCamelController.browseInflightExchanges("context1", null, 0, false);

        assertEquals("Context should contain three inflight exchanges", 3, inflightExchanges.size());
    }

    @Test
    public void testBrowseInflightExchangesWithNoRoutes() throws Exception {
        final List<Map<String, Object>> inflightExchanges = localCamelController.browseInflightExchanges("context1", null, 0, false);

        assertTrue("Context without routes should not have any inflight exchanges", inflightExchanges.isEmpty());
    }

    @Test
    public void testBrowseInflightExchangesWithOneRoute() throws Exception {
        context.createProducerTemplate().asyncSendBody("direct:start1", "Start one");

        // let the exchange proceed
        Thread.sleep(50);

        final List<Map<String, Object>> inflightExchanges = localCamelController.browseInflightExchanges("context1", null, 0, false);

        assertEquals("Context should contain one inflight exchange", 1, inflightExchanges.size());
    }

    @Test
    public void testBrowseInflightExchangesWithSpecificRoute() throws Exception {
        context.createProducerTemplate().asyncSendBody("direct:start1", "Start one");
        context.createProducerTemplate().asyncSendBody("direct:start2", "Start two");
        context.createProducerTemplate().asyncSendBody("direct:start3", "Start three");

        // let the exchanges proceed
        Thread.sleep(50);

        final List<Map<String, Object>> inflightExchanges = localCamelController.browseInflightExchanges("context1", "route2", 0, false);

        assertEquals("Context should contain one inflight exchange for specific route", 1, inflightExchanges.size());
    }

    @Test
    public void testTransformer() throws Exception {
        List<Map<String, String>> transformers = localCamelController.getTransformers("context1");
        assertEquals(1, transformers.size());
        Map<String, String> dummyTransformer = transformers.get(0);
        assertEquals("context1", dummyTransformer.get("camelContextName"));
        assertEquals("DummyTransformer[scheme='null', from='xml:foo', to='json:bar']", dummyTransformer.get("description"));
        assertEquals(null, dummyTransformer.get("scheme"));
        assertEquals("xml:foo", dummyTransformer.get("from"));
        assertEquals("json:bar", dummyTransformer.get("to"));
        assertEquals("Started", dummyTransformer.get("state"));
    }

    @Test
    public void testValidator() throws Exception {
        List<Map<String, String>> validators = localCamelController.getValidators("context1");
        assertEquals(1, validators.size());
        Map<String, String> dummyValidator = validators.get(0);
        assertEquals("context1", dummyValidator.get("camelContextName"));
        assertEquals("DummyValidator[type='xml:foo']", dummyValidator.get("description"));
        assertEquals("xml:foo", dummyValidator.get("type"));
        assertEquals("Started", dummyValidator.get("state"));
    }

    public static class DummyTransformer extends Transformer {
        @Override
        public void transform(Message message, DataType from, DataType to) throws Exception {
        }
    }

    public static class DummyValidator extends Validator {
        @Override
        public void validate(Message message, DataType type) throws ValidationException {
        }
    }
}
