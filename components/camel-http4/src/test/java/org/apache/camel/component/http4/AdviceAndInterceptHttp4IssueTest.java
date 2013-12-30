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
package org.apache.camel.component.http4;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 *
 */
public class AdviceAndInterceptHttp4IssueTest extends CamelTestSupport {

    private String simpleProvider = "http4:fakeHTTPADDRESS.com:80?throwExceptionOnFailure=false";
    private String providerWithParameter = "http4:fakeHTTPADDRESS.com:80?throwExceptionOnFailure=false&httpClient.cookieSpec=ignoreCookies";
    private volatile boolean messageIntercepted;

    @Test
    public void testHttp4WithoutHttpClientParameter() throws Exception {
        doTestHttp4Parameter(simpleProvider);
    }

    @Test
    public void testHttp4WithHttpClientParameter() throws Exception {
        doTestHttp4Parameter(providerWithParameter);
    }

    @Override
    public boolean isUseAdviceWith() {
        return true;
    }

    @Override
    public boolean isUseRouteBuilder() {
        return true;
    }

    private void doTestHttp4Parameter(final String provider) throws Exception {
        messageIntercepted = false;

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to(provider)
                        .to("mock:result");
            }
        });

        context.getRouteDefinitions().get(0).adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("http4:fakeHTTPADDRESS.com:80*")
                        .skipSendToOriginalEndpoint()
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                messageIntercepted = true;
                            }
                        })
                        .to("mock:advised");
            }
        });

        context.start();

        getMockEndpoint("mock:advised").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        assertTrue(messageIntercepted);
    }

}
