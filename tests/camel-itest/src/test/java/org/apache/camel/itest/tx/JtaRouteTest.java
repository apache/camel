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
package org.apache.camel.itest.tx;

import org.apache.camel.EndpointInject;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.transaction.RequiresNewJtaTransactionPolicy;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class JtaRouteTest extends CamelTestSupport {
    @EndpointInject("mock:splitted")
    private MockEndpoint splitted;

    @EndpointInject("mock:test")
    private MockEndpoint test;

    @EndpointInject("direct:requires_new")
    private ProducerTemplate start;

    @EndpointInject("direct:route1")
    private ProducerTemplate multicast;

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.getRegistry().bind("PROPAGATION_REQUIRES_NEW", new RequiresNewJtaTransactionPolicy());

                from("direct:requires_new")
                        .transacted("PROPAGATION_REQUIRES_NEW")
                        .split(body()).delimiter("_").to("direct:splitted").end()
                        .log("after splitter log which you will never see...")
                        .transform().constant("requires_new");

                from("direct:splitted").to("mock:splitted");

                from("direct:route1").routeId("r.route1")
                        .log(LoggingLevel.DEBUG, "Entering route: ${routeId}")
                        .transacted()
                        .to("direct:route2")
                        .log("will never get here");

                from("direct:route2").routeId("r.route2")
                        .log(LoggingLevel.DEBUG, "Entering route: ${routeId}")
                        .multicast()
                        .to("log:r.test", "direct:route3", "mock:test")
                        .end();

                from("direct:route3").routeId("r.route3")
                        .log(LoggingLevel.DEBUG, "Entering route: ${routeId}");

            }
        };
    }

    @Test
    void testTransactedSplit() throws Exception {
        splitted.expectedBodiesReceived("requires", "new");

        start.sendBody("requires_new");

        splitted.assertIsSatisfied();
    }

    @Test
    public void testMultiCast() throws InterruptedException {
        test.setExpectedMessageCount(1);

        multicast.sendBody("direct:route1", "msg");

        test.assertIsSatisfied();
    }
}
