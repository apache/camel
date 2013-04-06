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


import org.apache.camel.ContextTestSupport;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

public class RecipientListIgnoreInvalidEndpointsTest extends ContextTestSupport {

    public void testRecipientListWithIgnoreInvalidEndpointsOption() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("Hello World");
        
        MockEndpoint endpointA = getMockEndpoint("mock:endpointA");
        endpointA.expectedBodiesReceived("Hello a");

        template.requestBody("direct:startA", "Hello World", String.class);        

        assertMockEndpointsSatisfied();
    }
    
    public void testRecipientListWithoutIgnoreInvalidEndpointsOption() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(0);
        
        MockEndpoint endpointA = getMockEndpoint("mock:endpointA");
        endpointA.expectedMessageCount(0);
        
        try {
            template.requestBody("direct:startB", "Hello World", String.class);
            fail("Expect the exception here.");
        } catch (Exception ex) {
            assertTrue("Get a wrong cause of the exception", ex.getCause() instanceof ResolveEndpointFailedException);                         
        }

        assertMockEndpointsSatisfied();
    }
    
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:startA").recipientList(simple("mock:result,fail:endpoint,direct:a")).ignoreInvalidEndpoints();

                from("direct:startB").recipientList(simple("mock:result,fail:endpoint,direct:a"));
                
                from("direct:a").transform(constant("Hello a")).to("mock:endpointA");
               
            }
        };
    }

   
}
