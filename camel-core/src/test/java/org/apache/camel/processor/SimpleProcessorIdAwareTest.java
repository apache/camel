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
package org.apache.camel.processor;

import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.SendDefinition;
import org.apache.camel.spi.IdAware;

/**
 * @version 
 */
public class SimpleProcessorIdAwareTest extends ContextTestSupport {

    public void testIdAware() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        List<Processor> matches = context.getRoute("foo").filter("b*");
        assertEquals(2, matches.size());

        Processor bar = matches.get(0);
        Processor baz = matches.get(1);

        assertEquals("bar", ((IdAware) bar).getId());
        assertEquals("baz", ((IdAware) baz).getId());

        bar = context.getProcessor("bar");
        assertNotNull(bar);

        baz = context.getProcessor("baz");
        assertNotNull(baz);

        Processor unknown = context.getProcessor("unknown");
        assertNull(unknown);

        Processor result = context.getProcessor("result");
        assertNotNull(result);

        ProcessorDefinition def = context.getProcessorDefinition("result");
        assertNotNull(def);
        assertEquals("result", def.getId());
        SendDefinition send = assertIsInstanceOf(SendDefinition.class, def);
        assertNotNull(send);
        assertEquals("mock:result", send.getEndpointUri());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("foo")
                    .choice()
                        .when(header("bar"))
                        .to("log:bar").id("bar")
                    .otherwise()
                        .to("mock:result").id("result")
                    .end()
                    .to("log:baz").id("baz");
            }
        };
    }
}
