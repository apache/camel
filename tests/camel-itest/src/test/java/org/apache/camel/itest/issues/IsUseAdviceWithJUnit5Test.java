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
package org.apache.camel.itest.issues;

import org.apache.camel.builder.AdviceWith;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class IsUseAdviceWithJUnit5Test extends CamelTestSupport {

    private String providerEndPointURI = "http://fakeeeeWebsite.com:80";
    private String timerEndPointURI = "timer://myTimer";
    private String mockEndPointURI = "mock:myMock";
    private String directEndPointURI = "direct:myDirect";

    @Override
    protected RouteBuilder createRouteBuilder() {

        return new RouteBuilder() {
            @Override
            public void configure() {

                from(timerEndPointURI).to(providerEndPointURI).to(mockEndPointURI);
            }
        };
    }

    @Test
    void testIsUseAdviceWith() throws Exception {

        AdviceWith.adviceWith(context.getRouteDefinitions().get(0), context, new AdviceWithRouteBuilder() {
            public void configure() {

                replaceFromWith(directEndPointURI);

                interceptSendToEndpoint(providerEndPointURI).to("mock:intercepted").skipSendToOriginalEndpoint();
            }
        });

        // we must manually start when we are done with all the advice with
        context.start();

        getMockEndpoint(mockEndPointURI).expectedBodiesReceived("a trigger");
        getMockEndpoint("mock:intercepted").expectedBodiesReceived("a trigger");

        template.sendBody(directEndPointURI, "a trigger");

        MockEndpoint.assertIsSatisfied(context);

        assertNotNull(context.hasEndpoint(directEndPointURI));

        assertNotNull(context.hasEndpoint(mockEndPointURI));
    }

    @Override
    public boolean isUseAdviceWith() {
        return true;
    }

    @Override
    public boolean isUseRouteBuilder() {
        return true;
    }

}
