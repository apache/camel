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
package org.apache.camel.component.mina2;

import org.apache.camel.CamelContext;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * @version 
 */
public class Mina2SslGlobalContextParametersTcpTest extends BaseMina2Test {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.setSSLContextParameters(createSslContextParameters());
        ((SSLContextParametersAware) context.getComponent("mina2")).setUseGlobalSslContextParameters(true);
        return context;
    }

    @Test
    public void testMinaRoute() throws Exception {
        MockEndpoint endpoint = getMockEndpoint("mock:result");
        Object body = "Hello there!";
        endpoint.expectedBodiesReceived(body);

        template.sendBodyAndHeader("mina2:tcp://localhost:" + getPort() + "?sync=false&minaLogger=true", body, "cheese", 123);

        assertMockEndpointsSatisfied();
    }
    
    @Override
    protected boolean isUseSslContext() {
        return true;
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            public void configure() {
                fromF("mina2:tcp://localhost:%s?sync=false&minaLogger=true", getPort())
                        .to("log:before?showAll=true")
                        .to("mock:result").to("log:after?showAll=true");
            }
        };
    }
}
