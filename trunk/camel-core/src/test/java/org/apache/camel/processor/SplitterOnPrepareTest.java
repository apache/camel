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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

/**
 *
 */
public class SplitterOnPrepareTest extends ContextTestSupport {

    public void testSplitterOnPrepare() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(2);
        getMockEndpoint("mock:a").allMessages().body(String.class).isEqualTo("1 Tony the Tiger");

        List<Animal> animals = new ArrayList<Animal>();
        animals.add(new Animal(1, "Tiger"));
        animals.add(new Animal(1, "Tiger"));
        template.sendBody("direct:start", animals);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .split(body()).onPrepare(new FixNamePrepare())
                        .to("direct:a");

                from("direct:a").process(new ProcessorA()).to("mock:a");
            }
        };
    }

    public static class ProcessorA implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            Animal body = exchange.getIn().getBody(Animal.class);
            assertEquals(1, body.getId());
            assertEquals("Tony the Tiger", body.getName());
        }
    }

    public static final class FixNamePrepare implements Processor {

        public void process(Exchange exchange) throws Exception {
            Animal body = exchange.getIn().getBody(Animal.class);
            assertEquals(1, body.getId());
            assertEquals("Tiger", body.getName());

            body.setName("Tony the Tiger");
        }
    }
}
