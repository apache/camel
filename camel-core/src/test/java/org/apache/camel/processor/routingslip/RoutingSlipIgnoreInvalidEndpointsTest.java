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
package org.apache.camel.processor.routingslip;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

public class RoutingSlipIgnoreInvalidEndpointsTest extends ContextTestSupport {
    
    public void testEndpointResolvedFailedWithIgnoreInvalidEndpoints() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("Hello World");
        MockEndpoint end = getMockEndpoint("mock:end");
        end.expectedBodiesReceived("Hello World");
        
        template.sendBodyAndHeader("direct:a", "Hello", "myHeader", "direct:start ,fail:endpoint, mock:result");        

        assertMockEndpointsSatisfied();
    }
    
    public void testEndpointResolvedFailedWithoutIgnoreInvalidEndpoints() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(0);
        MockEndpoint end = getMockEndpoint("mock:end");
        end.expectedMessageCount(0);
        try {        
            template.sendBodyAndHeader("direct:b", "Hello", "myHeader", "direct:start,fail:endpoint,mock:result");        
            fail("Expect the exception here.");
        } catch (Exception ex) {
            assertTrue("Get a wrong cause of the exception", ex.getCause() instanceof ResolveEndpointFailedException);
        }
        assertMockEndpointsSatisfied();
    }
    
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {                
                from("direct:a").routingSlip(header("myHeader"))
                    .ignoreInvalidEndpoints().to("mock:end");
                
                from("direct:b").routingSlip(header("myHeader")).to("mock:end");
                
                from("direct:start").transform(constant("Hello World"));
            }
                
        };
    }

}

