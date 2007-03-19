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
package org.apache.camel;

import junit.framework.TestCase;
import org.apache.camel.builder.DestinationBuilder;
import org.apache.camel.builder.RouteBuilder;

import java.util.List;

/**
 * @version $Revision$
 */
public class RouteBuilderTest extends TestCase {

    public void testSimpleRoute() throws Exception {
        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                from("seda://a").to("seda://b");
            }
        };

        List<DestinationBuilder> destinationBuilders = builder.getRoutes();
        assertEquals("Number or destinationBuilders created", 1, destinationBuilders.size());
        DestinationBuilder destinationBuilder = destinationBuilders.get(0);
        Processor processor = destinationBuilder.createProcessor();
        assertTrue("Processor should be a SendProcessor but was: " + processor + " with type: " + processor.getClass().getName(), processor instanceof SendProcessor);
        SendProcessor sendProcessor = (SendProcessor) processor;
        assertEquals("Endpoint URI", "seda://b", sendProcessor.getDestination().getEndpointUri());
    }


    public void testSimpleRouteWithHeaderPredicate() throws Exception {
        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                from("seda://a").filter(headerEquals("foo", "bar")).to("seda://b");
            }
        };
    }

    public void testSimpleRouteWithChoice() throws Exception {
        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                from("seda://a").choice()
                        .when(headerEquals("foo", "bar")).to("seda://b")
                        .when(headerEquals("foo", "cheese")).to("seda://c")
                        .otherwise().to("seda://d");
            }
        };
    }
}
