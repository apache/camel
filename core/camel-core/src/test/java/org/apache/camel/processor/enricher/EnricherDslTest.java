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
package org.apache.camel.processor.enricher;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class EnricherDslTest extends ContextTestSupport {

    @Test
    public void testEnrich() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:enriched");
        mock.expectedBodiesReceived("res-1", "res-2", "res-3");

        template.sendBody("direct:start", 1);
        template.sendBody("direct:start", 2);
        template.sendBody("direct:start", 3);

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").enrichWith("direct:resource").body(Integer.class, String.class, (o, n) -> n + o).to("mock:enriched");

                // set an empty message
                from("direct:resource").transform().body(b -> "res-");
            }
        };
    }
}
